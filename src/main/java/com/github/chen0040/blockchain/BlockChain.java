package com.github.chen0040.blockchain;

import com.alibaba.fastjson.JSON;
import com.github.chen0040.blockchain.utils.HttpClient;
import com.github.chen0040.blockchain.utils.IpTools;
import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
@Setter
public class BlockChain {
    private List<Block> chain = new ArrayList<>();
    private List<Transaction> currentTransactions = new ArrayList<>();
    private Set<String> nodes = new HashSet<>();
    private final String id;
    private static final Logger logger = LoggerFactory.getLogger(BlockChain.class);

    public BlockChain(int port){
        id = "http://" + IpTools.getIpAddress() + ":" + port;
    }

    public Block newBlock(long proof) {
        return newBlock(proof, null);
    }


    public Block newBlock(long proof, String prev_hash){
        if(prev_hash == null) {
            prev_hash = hash(chain.get(chain.size()-1));
        }

        Block block = new Block();
        block.setIndex(chain.size()+1);
        block.setTimestamp(new Date().getTime());
        block.setTransactions(currentTransactions);
        block.setPrevHash(prev_hash);
        block.setProof(proof);

        currentTransactions = new ArrayList<>();

        chain.add(block);

        return block;
    }

    public long newTransaction(String sender, String recipient, double amount) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setSender(sender);
        transaction.setRecipient(recipient);

        return lastBlock().getIndex()+1; // return the index of the block that will hold this transaction
    }



    private Block lastBlock() {
        if(chain.isEmpty()) {
            return new Block();
        }
        return chain.get(chain.size()-1);
    }


    private String hash(Block block) {
        String json = JSON.toJSONString(block);
       return hash(json);
    }

    private String hash(String text) {
        return Hashing.sha256()
                .hashString(text, StandardCharsets.UTF_8)
                .toString();
    }

    public boolean validateProof(long lastProof, long proof) {
        String text = lastProof + "" + proof;
        String hashed = hash(text);
        return hashed.startsWith("0000");
    }

    public long proofOfWork(long lastProof) {
        long proof = 0L;
        while(!validateProof(lastProof,proof)) {
            proof++;
        }
        return proof;
    }

    public void registerNode(String url) {
        if(url.equals(id)) return;
        nodes.add(url);
    }

    public boolean validateChain(List<Block> chain) {
        Block lastBlock = chain.get(0);
        int currentIndex = 1;
        while(currentIndex < chain.size()) {
            Block block = chain.get(currentIndex);
            if(!block.getPrevHash().equals(hash(lastBlock))) {
                return false;
            }
            lastBlock = block;
            currentIndex++;
        }

        return true;
    }

    public boolean resolveConflicts() {
        List<String> neighbors = new ArrayList<>(nodes);
        List<Block> newChain = null;

        // only looking for chains longer than that available at this node.
        int max_length = chain.size();

        for(String neighbor : neighbors) {
            List<Block> chain = queryChain(neighbor);
            if(chain == null) {
                continue;
            }
            if(chain.size() <= max_length) {
                continue;
            }
            if(!validateChain(chain)) {
                continue;
            }
            max_length = chain.size();
            newChain = chain;
        }

        if(newChain != null) {
            chain = newChain;
            return true;
        }
        return false;


    }

    private List<Block> queryChain(String url) {
        return HttpClient.getArray(url + "/chain", Block.class);
    }

    public MineResult mine(){
        Block lastBlock = lastBlock();
        long lastProof = lastBlock.getProof();
        long proof = proofOfWork(lastProof);

        newTransaction("0", id, 1);

        String prevHash = hash(lastBlock);
        Block newBlock = newBlock(proof, prevHash);

        MineResult result = new MineResult();

        result.setIndex(newBlock.getIndex());
        result.setMessage("New Block Forged");
        result.setPrevHash(prevHash);
        result.setProof(proof);
        result.setTransactions(newBlock.getTransactions());

        return result;
    }

    public int register(List<String> nodes) {
        for(String node : nodes) {
            registerNode(node);
        }
        return this.nodes.size();
    }


    public void broadCast(String seedIp) {
        if(id.equals(seedIp)) return;
        List<String> a= new ArrayList<>();
        a.add(id);
        String url = seedIp + "/nodes/broadcast_ip";
        logger.info("broad cast this ip {} to {}", seedIp, url);
        HttpClient.postArray(url, a);
    }

    public void deRegisterSelf(String seedIp) {
        if(id.equals(seedIp)) return;
        List<String> a= new ArrayList<>();
        a.add(id);
        String url = seedIp + "/nodes/broadcast_de_registration";
        logger.info("broad cast the de-registration of this ip {} to {}", seedIp, url);
        HttpClient.postArray(url, a);
    }

    public int deRegister(List<String> nodes) {
        for(String node : nodes) {
            this.nodes.remove(node);
        }
        return this.nodes.size();
    }
}
