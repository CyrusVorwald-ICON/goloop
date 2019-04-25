package foundation.icon.test.common;

import foundation.icon.icx.KeyWallet;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertNotNull;

public class Env {
    public static final Log LOG = Log.getGlobal();
    public static Node []nodes;
    public static Chain []chains;
    public static int testApiVer = 3;
    private static String dataPath;

    public static class Node {
        private String url;
        public Channel []channels;
        public KeyWallet wallet;
        Node(String url, KeyWallet wallet) {
            this.url = url;
            this.wallet = wallet;
        }
    }

    public static class Chain {
        public int networkId;
        private List<Channel> channelList;
        public Channel []channels;
        public KeyWallet godWallet;
        public KeyWallet governorWallet;
        Chain(int networkId, KeyWallet god, KeyWallet governor) {
            this.networkId = networkId;
            this.godWallet = god;
            this.governorWallet = governor;
            this.channelList = new LinkedList<>();
        }

        private void makeChannels() {
            channels = channelList.toArray(new Channel[channelList.size()]);
            channelList = null;
        }
    }

    public static class Channel {
        public Node node;
        public String name;
        public Chain chain;

        Channel(Node node, String name, Chain chain) {
            this.node = node;
            this.name = name;
            this.chain = chain;
        }

        public String getAPIUrl(int v) {
            return node.url + "/api/v" + v + "/" + name;
        }
    }

    private static Map<String,Chain> readChains(Properties props) {
        Map<String, Chain> chainMap = new HashMap<>();
        for(int i = 0; ; i++) {
            String chainName = "chain" + i;

            String nid = props.getProperty(chainName + ".nid");
            if (nid == null) {
                if( i == 0 ) {
                    System.out.println("FAIL. no nid for chain");
                    throw new IllegalStateException("FAIL. no nid for channel");
                }
                break;
            }
            String godWalletPath = dataPath + props.getProperty(chainName + ".godWallet");
            String godPassword = props.getProperty(chainName + ".godPassword");
            KeyWallet godWallet = null;
            try {
                godWallet = Utils.readWalletFromFile(godWalletPath, godPassword);
            }
            catch (IOException ex) {
                System.out.println("FAIL to read god wallet. path = " + godWalletPath);
                throw new IllegalArgumentException("FAIL to read god wallet. path = " + godWalletPath);
            }
            String govWalletPath = props.getProperty(chainName + ".govWallet");
            String govPassword = props.getProperty(chainName + ".govPassword");
            KeyWallet governorWallet = null;
            if(govWalletPath == null) {
                try {
                    governorWallet = KeyWallet.create();
                }
                catch(Exception ex) {
                    System.out.println("FAIL to create wallet for governor!");
                    throw new IllegalArgumentException("FAIL to create wallet for governor!");
                }
            }
            else {
                try {
                    Utils.readWalletFromFile(govWalletPath, govPassword);
                }
                catch (IOException ex) {
                    System.out.println("FAIL to read governor wallet. path = " + govWalletPath);
                    throw new IllegalArgumentException("FAIL to read governor wallet. path = " + govWalletPath);
                }
            }
            Chain chain = new Chain(Integer.parseInt(nid), godWallet, governorWallet);
            chainMap.put(nid, chain);
        }
        return chainMap;
    }

    private static List<Node> readNodes(Properties props, Map<String, Chain> chainMap) {
        List<Node> nodeList = new LinkedList<>();
        for( int i = 0; ; i++ ) {
            String nodeName = "node" + i;
            String url = props.getProperty(nodeName + ".url");
            if( url == null ) {
                if(i == 0) {
                    System.out.println("FAIL. no node url");
                    throw new IllegalStateException("FAIL. no node url");
                }
                break;
            }
            String nodeWalletName =  props.getProperty(nodeName + ".wallet");
            KeyWallet nodeWallet = null;
            if(nodeWalletName != null) {
                String nodeWalletPassword = props.getProperty(nodeName + ".walletPassword");
                try {
                    nodeWallet = Utils.readWalletFromFile(dataPath + nodeWalletName, nodeWalletPassword);
                }
                catch (IOException ex) {
                    System.out.println("FAIL to read node wallet. path = " + nodeWalletName);
                    throw new IllegalArgumentException("FAIL to read node wallet. path = " + nodeWalletName);
                }
            }

            Node node = new Node(url, nodeWallet);
            // read channel env
            List<Channel> channelsOnNode = new LinkedList<>();
            for( int j = 0; ; j++ ) {
                String channelName = nodeName + ".channel" + j;
                String nid = props.getProperty(channelName + ".nid");
                if( nid == null ) {
                    if(j == 0) {
                        System.out.println("FAIL. no nid for channel");
                        throw new IllegalArgumentException("FAIL. no nid for channel");
                    }
                    break;
                }
                Chain chain = chainMap.get(nid);
                if(chain == null) {
                    System.out.println("FAIL. no chain for the " + nid);
                    throw new IllegalStateException("FAIL. no chain for the " + nid);
                }
                String name = props.getProperty(channelName + ".name", "default");
                Channel channel = new Channel(node, name, chain);
                channelsOnNode.add(channel);
                chain.channelList.add(channel);
            }
            node.channels = channelsOnNode.toArray(new Channel[channelsOnNode.size()]);
            nodeList.add(node);
        }
        for(Chain chain : chainMap.values()) {
            chain.makeChannels();
        }
        return nodeList;
    }

    static {
        String env_file = System.getProperty("CHAIN_ENV",
                "./data/env.properties");
        int lastIndex;
        if((lastIndex = env_file.lastIndexOf('/')) != -1) {
            dataPath = env_file.substring(0, lastIndex + 1);
        }
        Properties props = new Properties();
        try {
            FileInputStream fi = new FileInputStream(env_file);
            props.load(fi);
            fi.close();
        } catch (IOException e) {
            System.out.println("There is no environment file name=" + env_file);
            throw new IllegalStateException("There is no environment file name=" + env_file);
        }
        Map<String, Chain> chainMap = readChains(props);
        assertNotNull(chainMap);
        Env.chains = chainMap.values().toArray(new Chain[chainMap.size()]);

        List<Node> nodeList = readNodes(props, chainMap);
        assertNotNull(nodeList);
        Env.nodes = nodeList.toArray(new Node[nodeList.size()]);
    }
}
