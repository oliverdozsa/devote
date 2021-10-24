package devote.blockchain.mockblockchain;

import devote.blockchain.api.BlockchainConfiguration;
import devote.blockchain.api.IssuerAccount;

import java.util.HashSet;
import java.util.Set;

public class MockBlockchainIssuerAccount implements IssuerAccount {
    public static final int NUM_OF_ISSUER_ACCOUNTS_TO_CREATE = 2;

    private MockBlockchainConfiguration config;
    private boolean isInitCalled = false;

    private static int currentIssuerAccountId = 0;

    @Override
    public void init(BlockchainConfiguration config) {
        this.config = (MockBlockchainConfiguration) config;
        isInitCalled = true;
    }

    @Override
    public String create(long votesCap) {
        currentIssuerAccountId++;
        return Integer.toString(currentIssuerAccountId);
    }

    @Override
    public int calcNumOfAccountsNeeded(long votesCap) {
        return NUM_OF_ISSUER_ACCOUNTS_TO_CREATE;
    }

    public MockBlockchainConfiguration getConfig() {
        return config;
    }

    public boolean isInitCalled() {
        return isInitCalled;
    }

    public static boolean isCreated(String account) {
        int accountValue = Integer.parseInt(account);
        return accountValue <= currentIssuerAccountId;
    }
}
