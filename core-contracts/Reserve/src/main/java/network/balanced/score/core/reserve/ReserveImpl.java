/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.reserve;

import network.balanced.score.lib.interfaces.Reserve;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;
import static network.balanced.score.core.reserve.Constants.*;
import static network.balanced.score.lib.utils.Check.*;

public class ReserveImpl implements Reserve {

    private final VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    private final VarDB<BigInteger> ownerPercentage = Context.newVarDB(OWNER_REWARD, BigInteger.class);

    public ReserveImpl() {
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    @External(readonly = true)
    public Address getStakingAddress() {
        return staking.get();
    }

    @External
    public void setStakingAddress(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + ": Address provided is an EOA address. A contract " +
                "address is required.");
        staking.set(_address);
    }

    @External
    public void setOwnerPercentage(BigInteger _value) {
        onlyOwner();
        ownerPercentage.set(_value);
    }

    @External(readonly = true)
    public BigInteger getOwnerPercentage() {
        return ownerPercentage.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getBalances() {
        return  Context.getBalance(Context.getAddress());
    }

    @External
    public void fallback() {

    }

    @External
    public void transferOwnerRewards(){
        onlyOwner();
        BigInteger rewardsToOwner = (ownerPercentage.getOrDefault(BigInteger.ZERO).multiply(getBalances())).divide(ONE_EXA).divide(HUNDRED);
        Address ownerAddress = Context.getOwner();
        Context.transfer(ownerAddress, rewardsToOwner);
        FundTransfer(ownerAddress, rewardsToOwner, "ICX sent to owner");
    }

    @EventLog(indexed = 1)
    public void FundTransfer(Address _address, BigInteger reward, String note) {
    }
}