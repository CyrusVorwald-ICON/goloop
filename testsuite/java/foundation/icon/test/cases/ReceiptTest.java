package foundation.icon.test.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.ConfirmedTransaction;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcError;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.common.Constants;
import foundation.icon.test.common.Env;
import foundation.icon.test.common.ResultTimeoutException;
import foundation.icon.test.common.Utils;
import foundation.icon.test.score.Score;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static foundation.icon.test.common.Env.LOG;
import static org.junit.jupiter.api.Assertions.*;

@Tag(Constants.TAG_PY_SCORE)
class ReceiptTest {
    private static IconService iconService;
    private static Env.Chain chain;
    private static KeyWallet ownerWallet;
    private static KeyWallet callerWallet;
    private static Score testScore;
    private static Score interCallScore;
    private static final String PATH = Constants.SCORE_RECEIPT_PATH;

    @BeforeAll
    public static void init() throws Exception {
        Env.Node node = Env.nodes[0];
        Env.Channel channel = node.channels[0];
        chain = channel.chain;
        iconService = new IconService(new HttpProvider(channel.getAPIUrl(Env.testApiVer)));
        initScoreTest();
    }

    private static void initScoreTest() throws Exception {
        ownerWallet = KeyWallet.create();
        callerWallet = KeyWallet.create();
        Address[] addrs = {ownerWallet.getAddress(), callerWallet.getAddress(), chain.governorWallet.getAddress()};
        Utils.transferAndCheck(iconService, chain, chain.godWallet, addrs, Constants.DEFAULT_BALANCE);

        RpcObject params = new RpcObject.Builder()
                .build();
        Address interCallAddr = Score.install(iconService, chain, ownerWallet, PATH, params);
        interCallScore = new Score(iconService, chain, interCallAddr);

        Address scoreAddr = Score.install(iconService, chain, ownerWallet, PATH, params);
        testScore = new Score(iconService, chain, scoreAddr);
    }

    @Test
    public void eventLog() throws Exception {
        LOG.infoEntering("eventLog");
        String[] signatures = new String[]{
                "event_log_no_index(bool,Address,int,bytes,str)",
                "event_log_1_index(bool,Address,int,bytes,str)",
                "event_log_2_index(bool,Address,int,bytes,str)",
                "event_log_3_index(bool,Address,int,bytes,str)",
        };
        String[] inputs = new String[]{
                "0x1",
                interCallScore.getAddress().toString(),
                "0x" + BigInteger.TEN.abs().toString(16),
                new Bytes(new byte[]{1, 2, 3}).toHexString(true),
                "log test"
        };
        for (int i = 0; i < signatures.length; i++) {
            RpcObject params = new RpcObject.Builder()
                    .put("p_log_index", new RpcValue(BigInteger.valueOf(i)))
                    .put("p_bool", new RpcValue(inputs[0]))
                    .put("p_addr", new RpcValue(inputs[1]))
                    .put("p_int", new RpcValue(inputs[2]))
                    .put("p_bytes", new RpcValue(inputs[3]))
                    .put("p_str", new RpcValue(inputs[4]))
                    .build();
            LOG.infoEntering("invoke call_event_log index(" + i + ")");
            TransactionResult result =
                    testScore.invokeAndWaitResult(callerWallet, "call_event_log",
                            params, BigInteger.valueOf(0), BigInteger.valueOf(100));
            LOG.infoExiting();
            assertEquals(Constants.STATUS_SUCCESS, result.getStatus());

            boolean found = false;
            for (TransactionResult.EventLog event : result.getEventLogs()) {
                if (event.getScoreAddress().compareTo(testScore.getAddress().toString()) == 0) {
                    found = true;
                    String signature = event.getIndexed().get(0).asString();
                    assertEquals(signatures[i], signature);
                    for (int j = 1; j <= i; j++) {
                        String indexed = event.getIndexed().get(j).asString();
                        assertEquals(inputs[j - 1], indexed);
                    }
                    for (int k = 0; k < inputs.length - i; k++) {
                        assertEquals(inputs[k + i], event.getData().get(k).asString());
                    }
                }
            }
            assertEquals(true, found);
        }
        LOG.infoExiting();
    }

    @Test
    public void interCallEventLog() throws Exception {
        LOG.infoEntering("interCallEventLog");
        String[] signatures = new String[]{
                "event_log_no_index(bool,Address,int,bytes,str)",
                "event_log_1_index(bool,Address,int,bytes,str)",
                "event_log_2_index(bool,Address,int,bytes,str)",
                "event_log_3_index(bool,Address,int,bytes,str)",
        };
        String[] inputs = new String[]{
                "0x1",
                interCallScore.getAddress().toString(),
                "0x" + BigInteger.TEN.abs().toString(16),
                new Bytes(new byte[]{1, 2, 3}).toHexString(true),
                "log test"
        };

        for (int i = 0; i < signatures.length; i++) {
            RpcObject params = new RpcObject.Builder()
                    .put("_to", new RpcValue(interCallScore.getAddress()))
                    .put("p_log_index", new RpcValue(BigInteger.valueOf(i)))
                    .put("p_bool", new RpcValue(inputs[0]))
                    .put("p_addr", new RpcValue(inputs[1]))
                    .put("p_int", new RpcValue(inputs[2]))
                    .put("p_bytes", new RpcValue(inputs[3]))
                    .put("p_str", new RpcValue(inputs[4]))
                    .build();
            LOG.infoEntering("invoke call_event_log index(" + i + ")");
            TransactionResult result =
                    testScore.invokeAndWaitResult(callerWallet, "inter_call_event_log",
                            params, BigInteger.valueOf(0), BigInteger.valueOf(100));
            LOG.infoExiting();
            assertEquals(Constants.STATUS_SUCCESS, result.getStatus());

            boolean found = false;
            for (TransactionResult.EventLog event : result.getEventLogs()) {
                if (event.getScoreAddress().compareTo(interCallScore.getAddress().toString()) == 0) {
                    found = true;
                    String signature = event.getIndexed().get(0).asString();
                    assertEquals(signatures[i], signature);
                    for (int j = 1; j <= i; j++) {
                        String indexed = event.getIndexed().get(j).asString();
                        assertEquals(inputs[j - 1], indexed);
                    }
                    for (int k = 0; k < inputs.length - i; k++) {
                        assertEquals(inputs[k + i], event.getData().get(k).asString());
                    }
                }
            }
            assertEquals(true, found);
        }
        LOG.infoExiting();
    }

    @Test
    public void logsBloomWithNoIndex() throws Exception {
        LOG.infoEntering("logsBloomWithNoIndex");
        TransactionResult[] txResult = new TransactionResult[3];
        for (int i = 0; i < 3; i++) {
            RpcObject.Builder builder = new RpcObject.Builder()
                    .put("p_log_index", new RpcValue(BigInteger.valueOf(0)))
                    .put("p_addr", new RpcValue(testScore.getAddress()))
                    .put("p_int", new RpcValue(BigInteger.TEN))
                    .put("p_bytes", new RpcValue(new byte[]{1, 2, 3}))
                    .put("p_str", new RpcValue("log test"));
            if (i == 2) {
                builder.put("p_bool", new RpcValue(true));
            } else {
                builder.put("p_bool", new RpcValue(false));
            }
            RpcObject params = builder.build();
            LOG.infoEntering("invoke call_event_log");
            txResult[i] =
                    testScore.invokeAndWaitResult(callerWallet, "call_event_log",
                            params, BigInteger.valueOf(0), BigInteger.valueOf(100));
            LOG.infoExiting();
            assertEquals(Constants.STATUS_SUCCESS, txResult[i].getStatus());
        }
        assertNotEquals(null, txResult[0].getLogsBloom());
        assertNotEquals(null, txResult[1].getLogsBloom());
        assertNotEquals(null, txResult[2].getLogsBloom());

        assertEquals(txResult[0].getLogsBloom(), txResult[1].getLogsBloom());
        assertEquals(txResult[0].getLogsBloom(), txResult[2].getLogsBloom());
        LOG.infoExiting();
    }

    @Test
    public void logsBloomWithIndex() throws Exception {
        LOG.infoEntering("logsBloomWithIndex");
        TransactionResult[] txResult = new TransactionResult[3];
        for (int i = 0; i < 3; i++) {
            RpcObject.Builder builder = new RpcObject.Builder()
                    .put("p_log_index", new RpcValue(BigInteger.valueOf(2)))
                    .put("p_addr", new RpcValue(testScore.getAddress()))
                    .put("p_int", new RpcValue(BigInteger.TEN))
                    .put("p_bytes", new RpcValue(new byte[]{1, 2, 3}))
                    .put("p_str", new RpcValue("log test"));
            if (i == 2) {
                builder.put("p_bool", new RpcValue(true));
            } else {
                builder.put("p_bool", new RpcValue(false));
            }
            RpcObject params = builder.build();
            LOG.infoEntering("invoke call_event_log");
            txResult[i] =
                    testScore.invokeAndWaitResult(callerWallet, "call_event_log",
                            params, BigInteger.valueOf(0), BigInteger.valueOf(100));
            LOG.infoExiting();
            assertEquals(Constants.STATUS_SUCCESS, txResult[i].getStatus());
        }
        assertNotNull(txResult[0].getLogsBloom());
        assertNotNull(txResult[1].getLogsBloom());
        assertNotNull(txResult[2].getLogsBloom());

        assertEquals(txResult[0].getLogsBloom(), txResult[1].getLogsBloom());
        assertNotEquals(txResult[0].getLogsBloom(), txResult[2].getLogsBloom());
        LOG.infoExiting();
    }

    private static void checkResultParams(TransactionResult txr, BigInteger status, Address to, Bytes txHash) {
        assertEquals(status, txr.getStatus());
        assertEquals(to.toString(), txr.getTo());
        assertEquals(txHash, txr.getTxHash());
        assertNotNull(txr.getTxIndex());
        assertNotNull(txr.getBlockHeight());
        assertNotNull(txr.getBlockHash());
        assertNotNull(txr.getCumulativeStepUsed());
        assertNotNull(txr.getStepUsed());
        assertNotNull(txr.getStepPrice());
        assertNotNull(txr.getEventLogs());
        assertNotNull(txr.getLogsBloom());
    }

    @Test
    public void transferTxResultParams() throws Exception {
        LOG.infoEntering("deployTxResultParams");
        KeyWallet wallet = KeyWallet.create();
        LOG.infoEntering("transfer");
        Bytes txHash = Utils.transfer(iconService, chain.networkId, callerWallet, wallet.getAddress(), BigInteger.valueOf(2));
        LOG.infoExiting();
        TransactionResult result =
                Utils.getTransactionResult(iconService, txHash, Constants.DEFAULT_WAITING_TIME);
        checkResultParams(result, Constants.STATUS_SUCCESS, wallet.getAddress(), txHash);
        LOG.infoExiting();
    }

    @Test
    public void deployTxResultParams() throws Exception {
        LOG.infoEntering("deployTxResultParams");
        RpcObject params = new RpcObject.Builder()
                .build();
        LOG.infoEntering("deploy");
        Bytes txHash = Utils.deployScore(iconService, chain.networkId,
                callerWallet, Constants.CHAINSCORE_ADDRESS, PATH, params, Constants.DEFAULT_STEP_LIMIT);
        LOG.infoExiting();
        TransactionResult result =
                Utils.getTransactionResult(iconService, txHash, Constants.DEFAULT_WAITING_TIME);
        checkResultParams(result, Constants.STATUS_SUCCESS, Constants.CHAINSCORE_ADDRESS, txHash);
        assertNotNull(result.getScoreAddress());
        LOG.infoExiting();
    }

    @Test
    public void callTxResultParams() throws Exception {
        LOG.infoEntering("txResultParams");
        RpcObject params = new RpcObject.Builder()
                .put("p_log_index", new RpcValue(BigInteger.valueOf(3)))
                .put("p_bool", new RpcValue(false))
                .put("p_addr", new RpcValue(testScore.getAddress()))
                .put("p_int", new RpcValue(BigInteger.TEN))
                .put("p_bytes", new RpcValue(new byte[]{1, 2, 3}))
                .put("p_str", new RpcValue("log test"))
                .build();
        LOG.infoEntering("invoke call_event_log");
        Bytes txHash = testScore.invoke(callerWallet, "call_event_log",
                params, BigInteger.valueOf(0), BigInteger.valueOf(100));
        LOG.infoExiting();
        TransactionResult txr = testScore.waitResult(txHash);
        checkResultParams(txr, Constants.STATUS_SUCCESS, testScore.getAddress(), txHash);
        LOG.infoExiting();
    }

    private static ConfirmedTransaction invokeAndGetTxByHash(Bytes txHash) throws Exception {
        long limitTime = System.currentTimeMillis() + Constants.DEFAULT_WAITING_TIME;
        ConfirmedTransaction ctx = null;
        while(ctx == null) {
            try {
                ctx = iconService.getTransaction(txHash).execute();
            } catch (RpcError ex) {
                if (limitTime < System.currentTimeMillis()) {
                    throw new ResultTimeoutException(txHash);
                }
            }
        }
        return ctx;
    }

    private static void checkTxParams(ConfirmedTransaction ctx, Address from, Address to, BigInteger value,
                                      BigInteger stepLimit, BigInteger nid, BigInteger nonce, Bytes txHash,
                                      String dataType) {
        assertEquals(BigInteger.valueOf(Env.testApiVer), ctx.getVersion());
        assertEquals(from, ctx.getFrom());
        assertEquals(to, ctx.getTo());
        assertEquals(value, ctx.getValue());
        assertEquals(stepLimit, ctx.getStepLimit());
        assertNotNull(ctx.getTimestamp());
        assertEquals(nid, ctx.getNid());
        assertEquals(nonce, ctx.getNonce());
        assertEquals(txHash, ctx.getTxHash());
        assertNotNull(ctx.getTxIndex());
        assertNotNull(ctx.getBlockHeight());
        assertNotNull(ctx.getBlockHash());
        assertNotNull(ctx.getSignature());
        assertEquals(dataType, ctx.getDataType());
        if(dataType != null) {
            assertNotNull(ctx.getData());
        } else {
            assertNull(ctx.getData());
        }
    }

    @Test
    public void transferTxByHashParams() throws Exception {
        LOG.infoEntering("transferTxByHashParams");
        KeyWallet wallet = KeyWallet.create();
        LOG.infoEntering("transfer");
        BigInteger value = BigInteger.valueOf(2);
        Bytes txHash = Utils.transfer(iconService, chain.networkId, callerWallet, wallet.getAddress(), value);
        LOG.infoExiting();
        ConfirmedTransaction ctx = invokeAndGetTxByHash(txHash);
        checkTxParams(ctx, callerWallet.getAddress(), wallet.getAddress(), value,
                BigInteger.valueOf(Constants.DEFAULT_STEP_LIMIT), BigInteger.valueOf(Env.chains[0].networkId),
                BigInteger.ONE, txHash, null);
        LOG.infoExiting();
    }

    @Test
    public void callTxByHashParams() throws Exception {
        LOG.infoEntering("txByHashParams");
        RpcObject params = new RpcObject.Builder()
                .put("p_log_index", new RpcValue(BigInteger.valueOf(3)))
                .put("p_bool", new RpcValue(false))
                .put("p_addr", new RpcValue(testScore.getAddress()))
                .put("p_int", new RpcValue(BigInteger.TEN))
                .put("p_bytes", new RpcValue(new byte[]{1, 2, 3}))
                .put("p_str", new RpcValue("log test"))
                .build();
        LOG.infoEntering("invoke call_event_log");
        BigInteger stepLimit = BigInteger.valueOf(100);
        Bytes txHash = testScore.invoke(callerWallet, "call_event_log",
                params, BigInteger.valueOf(0), stepLimit);
        LOG.infoExiting();
        ConfirmedTransaction ctx = invokeAndGetTxByHash(txHash);
        checkTxParams(ctx, callerWallet.getAddress(), testScore.getAddress(), null,
                stepLimit, BigInteger.valueOf(Env.chains[0].networkId),
                null, txHash, "call");
        LOG.infoExiting();
    }
}
