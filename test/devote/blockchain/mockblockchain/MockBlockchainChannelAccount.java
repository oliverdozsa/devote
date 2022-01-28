package devote.blockchain.mockblockchain;

import devote.blockchain.api.BlockchainConfiguration;
import devote.blockchain.api.ChannelAccount;
import devote.blockchain.api.KeyPair;

public class MockBlockchainChannelAccount implements ChannelAccount {
    public static final int NUM_OF_CHANNEL_ACCOUNTS_TO_CREATE_IN_ON_BATCH = 11;

    private static int currentChannelAccountId = 0;

    @Override
    public void init(BlockchainConfiguration configuration) {
    }

    @Override
    public int maxNumOfAccountsToCreateInOneBatch() {
        return NUM_OF_CHANNEL_ACCOUNTS_TO_CREATE_IN_ON_BATCH;
    }

    @Override
    public KeyPair create(long votesCap, KeyPair issuerKeyPair) {
        currentChannelAccountId++;
        String currentChannelAccountIdAsString = Integer.toString(currentChannelAccountId);
        return new KeyPair(currentChannelAccountIdAsString, currentChannelAccountIdAsString);
    }

    public static boolean isCreated(String accountSecret) {
        int accountValue = Integer.parseInt(accountSecret);
        return accountValue <= currentChannelAccountId;
    }
}
