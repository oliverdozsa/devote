package ipfs.data;

import java.time.Instant;
import java.util.List;

public class IpfsVoting {
    private String title;
    private String network;
    private Long votesCap;
    private List<IpfsPoll> polls;
    private Instant createdAt;
    private Instant encryptedUntil;
    private Instant startDate;
    private Instant endDate;
    private String distributionAccountId;
    private String ballotAccountId;
    private String issuerAccountId;
    private String assetCode;
    private String authorization;
    private String authOptionKeybase;
    private String visibility;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public Long getVotesCap() {
        return votesCap;
    }

    public void setVotesCap(Long votesCap) {
        this.votesCap = votesCap;
    }

    public List<IpfsPoll> getPolls() {
        return polls;
    }

    public void setPolls(List<IpfsPoll> polls) {
        this.polls = polls;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEncryptedUntil() {
        return encryptedUntil;
    }

    public void setEncryptedUntil(Instant encryptedUntil) {
        this.encryptedUntil = encryptedUntil;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public String getDistributionAccountId() {
        return distributionAccountId;
    }

    public void setDistributionAccountId(String distributionAccountId) {
        this.distributionAccountId = distributionAccountId;
    }

    public String getBallotAccountId() {
        return ballotAccountId;
    }

    public void setBallotAccountId(String ballotAccountId) {
        this.ballotAccountId = ballotAccountId;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public String getAuthOptionKeybase() {
        return authOptionKeybase;
    }

    public void setAuthOptionKeybase(String authOptionKeybase) {
        this.authOptionKeybase = authOptionKeybase;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getIssuerAccountId() {
        return issuerAccountId;
    }

    public void setIssuerAccountId(String issuerAccountId) {
        this.issuerAccountId = issuerAccountId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }
}
