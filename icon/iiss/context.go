package iiss

import (
	"encoding/json"
	"math/big"

	"github.com/icon-project/goloop/common/errors"
	"github.com/icon-project/goloop/common/intconv"
	"github.com/icon-project/goloop/icon/icmodule"
	"github.com/icon-project/goloop/module"
	"github.com/icon-project/goloop/service/contract"
	"github.com/icon-project/goloop/service/scoredb"
	"github.com/icon-project/goloop/service/state"
)

func validateAmount(amount *big.Int) error {
	if amount == nil || amount.Sign() < 0 {
		return errors.Errorf("Invalid amount: %v", amount)
	}
	return nil
}

func setBalance(address module.Address, as state.AccountState, balance *big.Int) error {
	if balance.Sign() < 0 {
		return errors.Errorf(
			"Invalid balance: address=%v balance=%v",
			address, balance,
		)
	}
	as.SetBalance(balance)
	return nil
}

type worldContextImpl struct {
	state.WorldContext
}

func (ctx *worldContextImpl) Origin() module.Address {
	return ctx.TransactionInfo().From
}

func (ctx *worldContextImpl) GetBalance(address module.Address) *big.Int {
	account := ctx.GetAccountState(address.ID())
	return account.GetBalance()
}

func (ctx *worldContextImpl) Deposit(address module.Address, amount *big.Int) error {
	if err := validateAmount(amount); err != nil {
		return err
	}
	if amount.Sign() == 0 {
		return nil
	}
	return ctx.addBalance(address, amount)
}

func (ctx *worldContextImpl) Withdraw(address module.Address, amount *big.Int) error {
	if err := validateAmount(amount); err != nil {
		return err
	}
	if amount.Sign() == 0 {
		return nil
	}
	return ctx.addBalance(address, new(big.Int).Neg(amount))
}

func (ctx *worldContextImpl) Transfer(from module.Address, to module.Address, amount *big.Int) (err error) {
	if err = validateAmount(amount); err != nil {
		return
	}
	if amount.Sign() == 0 || from.Equal(to) {
		return nil
	}
	// Subtract amount from the balance of "from" address
	if err = ctx.addBalance(from, new(big.Int).Neg(amount)); err != nil {
		return
	}
	// Add amount to "to" address
	if err = ctx.addBalance(to, amount); err != nil {
		return
	}
	return
}

func (ctx *worldContextImpl) addBalance(address module.Address, amount *big.Int) error {
	as := ctx.GetAccountState(address.ID())
	ob := as.GetBalance()
	return setBalance(address, as, new(big.Int).Add(ob, amount))
}


func (ctx *worldContextImpl) GetTotalSupply() *big.Int {
	as := ctx.GetAccountState(state.SystemID)
	tsVar := scoredb.NewVarDB(as, state.VarTotalSupply)
	if ts := tsVar.BigInt(); ts != nil {
		return ts
	}
	return icmodule.BigIntZero
}

func (ctx *worldContextImpl) AddTotalSupply(amount *big.Int) (*big.Int, error) {
	as := ctx.GetAccountState(state.SystemID)
	varDB := scoredb.NewVarDB(as, state.VarTotalSupply)
	oldTs := varDB.BigInt()
	if oldTs == nil {
		oldTs = new(big.Int)
	}
	ts := new(big.Int).Add(oldTs, amount)
	if ts.Sign() < 0 {
		return nil, errors.Errorf("TotalSupply < 0")
	}
	return ts, varDB.Set(ts)
}

func (ctx *worldContextImpl) SetValidators(validators []module.Validator) error {
	return ctx.GetValidatorState().Set(validators)
}

func NewWorldContext(ctx state.WorldContext) icmodule.WorldContext {
	return &worldContextImpl{
		WorldContext: ctx,
	}
}

type callContextImpl struct {
	icmodule.WorldContext
	cc contract.CallContext
	from module.Address
}

func (ctx *callContextImpl) From() module.Address {
	return ctx.from
}

func (ctx *callContextImpl) Burn(address module.Address, amount *big.Int) error {
	sign := amount.Sign()
	if sign < 0 {
		return errors.Errorf("Invalid amount: %v", amount)
	}
	if sign > 0 {
		ts, err := ctx.AddTotalSupply(new(big.Int).Neg(amount))
		if err != nil {
			return err
		}
		ctx.OnBurn(address, amount, ts)
	}
	return nil
}

func (ctx *callContextImpl) OnBurn(address module.Address, amount, ts *big.Int) {
	rev := ctx.Revision().Value()
	if rev < icmodule.RevisionBurnV2 {
		var burnSig string
		if rev < icmodule.RevisionFixBurnEventSignature {
			burnSig = "ICXBurned"
		} else {
			burnSig = "ICXBurned(int)"
		}
		ctx.cc.OnEvent(state.SystemAddress,
			[][]byte{[]byte(burnSig)},
			[][]byte{intconv.BigIntToBytes(amount)},
		)
	} else {
		ctx.cc.OnEvent(state.SystemAddress,
			[][]byte{[]byte("ICXBurnedV2(Address,int,int)"), address.Bytes()},
			[][]byte{intconv.BigIntToBytes(amount), intconv.BigIntToBytes(ts)},
		)
	}
}

func (ctx *callContextImpl) SumOfStepUsed() *big.Int {
	return ctx.cc.SumOfStepUsed()
}

func (ctx *callContextImpl) OnEvent(addr module.Address, indexed, data [][]byte) {
	ctx.cc.OnEvent(addr, indexed, data)
}

func (ctx *callContextImpl) CallOnTimer(to module.Address, params []byte) error {
	cc := ctx.cc
	cm := cc.ContractManager()
	jso := &contract.DataCallJSON{Method: "onTimer", Params: params}
	callData, _ := json.Marshal(jso)
	sl := cc.GetStepLimit(state.StepLimitTypeInvoke)
	ch, err := cm.GetHandler(
		state.SystemAddress,
		to,
		new(big.Int),
		contract.CTypeCall,
		callData,
	)
	if err != nil {
		return err
	}
	if err, _, _, _ = cc.Call(ch, sl); err != nil {
		return err
	}
	return nil
}

func NewCallContext(cc contract.CallContext, from module.Address) icmodule.CallContext {
	return &callContextImpl{
		WorldContext: NewWorldContext(cc),
		cc: cc,
		from: from,
	}
}
