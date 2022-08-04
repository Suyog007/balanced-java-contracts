package network.balanced.score.lib.interfaces;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface Reserve {
    @External(readonly = true)
    String name();

    @External(readonly = true)
    Address getStakingAddress();

    @External
    void setStakingAddress(Address _address);

    @External(readonly = true)
    BigInteger getBalances();

    @External
    void fallback();

    @External
    void setOwnerPercentage(BigInteger _value);

    @External(readonly = true)
    BigInteger getOwnerPercentage();

    @External
    void transferOwnerRewards();
}
