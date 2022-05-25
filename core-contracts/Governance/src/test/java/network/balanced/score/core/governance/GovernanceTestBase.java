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

package network.balanced.score.core.governance;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Context;
import score.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.core.governance.interfaces.*;
import network.balanced.score.lib.interfaces.*;


import static network.balanced.score.core.governance.GovernanceConstants.*;

public class GovernanceTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();


    protected static final Account oracle = Account.newScoreAccount(scoreCount);

    protected MockContract<LoansScoreInterface> loans;
    protected MockContract<DexScoreInterface> dex;
    protected MockContract<StakingScoreInterface> staking;
    protected MockContract<RewardsScoreInterface> rewards;
    protected MockContract<ReserveScoreInterface> reserve;
    protected MockContract<DividendsScoreInterface> dividends; 
    protected MockContract<DAOfundScoreInterface> daofund;
    protected MockContract<SicxScoreInterface> sicx;
    protected MockContract<BalancedDollarScoreInterface> bnUSD; 
    protected MockContract<BalnScoreInterface> baln;
    protected MockContract<WorkerTokenScoreInterface> bwt;
    protected MockContract<RouterScoreInterface> router; 
    protected MockContract<RebalancingScoreInterface> rebalancing;
    protected MockContract<FeehandlerScoreInterface> feehandler;
    protected MockContract<StakedLpScoreInterface> stakedLp;

    protected BalancedAddresses balancedAddresses = new BalancedAddresses();

    protected Score governance;

    protected JsonObject createJsonDistribtion(String name, BigInteger dist) {
        JsonObject distribution = new JsonObject()
            .add("recipient_name", name)
            .add("dist_percent", dist.toString());
            
        return distribution;
    }
    
    protected JsonObject createJsonDisbusment(String token, BigInteger amount) {
        JsonObject distribution = new JsonObject()
            .add("address", token)
            .add("amount", amount.intValue());
            
        return distribution;
    }

    protected JsonObject createParameter(String type, String value) {
        JsonObject parameter = new JsonObject()
            .add("type", type)
            .add("value", value);

        return parameter;
    }

    protected JsonObject createParameter(String type, BigInteger value) {
        JsonObject parameter = new JsonObject()
            .add("type", type)
            .add("value", value.intValue());

        return parameter;
    }

    protected JsonObject createParameter(String type, Boolean value) {
        JsonObject parameter = new JsonObject()
            .add("type", type)
            .add("value", value);

        return parameter;
    }

    private void setupAddresses() {
        balancedAddresses.loans = loans.getAddress();
        balancedAddresses.dex = dex.getAddress();
        balancedAddresses.staking = staking.getAddress();
        balancedAddresses.rewards = rewards.getAddress();
        balancedAddresses.reserve = reserve.getAddress();
        balancedAddresses.dividends = dividends.getAddress();
        balancedAddresses.daofund = daofund.getAddress();
        balancedAddresses.oracle = oracle.getAddress();
        balancedAddresses.sicx = sicx.getAddress();
        balancedAddresses.bnUSD = bnUSD.getAddress();
        balancedAddresses.baln = baln.getAddress();
        balancedAddresses.bwt = bwt.getAddress();
        balancedAddresses.router = router.getAddress();
        balancedAddresses.rebalancing = rebalancing.getAddress();
        balancedAddresses.feehandler = feehandler.getAddress();
        balancedAddresses.stakedLp = stakedLp.getAddress();

        governance.invoke(owner, "setAddresses", balancedAddresses);
        governance.invoke(owner, "setContractAddresses");

        verify(loans.mock).setRewards(rewards.getAddress());
        verify(loans.mock).setDividends(dividends.getAddress());
        verify(loans.mock).setStaking(staking.getAddress());
        verify(loans.mock).setReserve(reserve.getAddress());

        verify(dex.mock).setRewards(rewards.getAddress());
        verify(dex.mock).setDividends(dividends.getAddress());
        verify(dex.mock).setStaking(staking.getAddress());
        verify(dex.mock).setSicx(sicx.getAddress());
        verify(dex.mock).setBnusd(bnUSD.getAddress());
        verify(dex.mock).setBaln(baln.getAddress());
        verify(dex.mock).setFeehandler(feehandler.getAddress());
        verify(dex.mock).setStakedLp(stakedLp.getAddress());

        verify(rewards.mock).setReserve(reserve.getAddress());
        verify(rewards.mock).setBwt(bwt.getAddress());
        verify(rewards.mock).setBaln(baln.getAddress());
        verify(rewards.mock).setDaofund(daofund.getAddress());
        verify(rewards.mock).setStakedLp(stakedLp.getAddress());
       
        verify(dividends.mock).setDex(dex.getAddress());
        verify(dividends.mock).setLoans(loans.getAddress());
        verify(dividends.mock).setDaofund(daofund.getAddress());
        verify(dividends.mock).setBaln(baln.getAddress());

        verify(daofund.mock).setLoans(loans.getAddress());

        verify(reserve.mock).setLoans(loans.getAddress());
        verify(reserve.mock).setBaln(baln.getAddress());
        verify(reserve.mock).setSicx(sicx.getAddress());

        verify(bnUSD.mock).setOracle(oracle.getAddress());

        verify(baln.mock).setDividends(dividends.getAddress());
        verify(baln.mock).setOracle(oracle.getAddress());
        verify(baln.mock).setDex(dex.getAddress());
        verify(baln.mock).setBnusd(bnUSD.getAddress());

        verify(bwt.mock).setBaln(baln.getAddress());

        verify(router.mock).setDex(dex.getAddress());
        verify(router.mock).setSicx(sicx.getAddress());
        verify(router.mock).setStaking(staking.getAddress());

        verify(stakedLp.mock).setDex(dex.getAddress());
        verify(stakedLp.mock).setRewards(rewards.getAddress());
    }

    protected BigInteger executeVoteWithActions(String actions) {
        sm.getBlock().increase(DAY);
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);

        when(baln.mock.totalSupply()).thenReturn(BigInteger.valueOf(20).multiply(ICX));
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.TEN.multiply(ICX));
        
        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(ICX));
        when(dex.mock.totalBalnAt(BALNBNUSD_ID, snapshot, false)).thenReturn(BigInteger.TWO.multiply(ICX));
        when(dex.mock.totalBalnAt(BALNSICX_ID, snapshot, false)).thenReturn(BigInteger.TWO.multiply(ICX));

        Map<String, Object> vote = getVote(id);
        goToDay((BigInteger)vote.get("start day"));

        when(baln.mock.stakedBalanceOfAt(owner.getAddress(), snapshot)).thenReturn(BigInteger.valueOf(8).multiply(ICX));
        governance.invoke(owner, "castVote", id, true);

        goToDay((BigInteger)vote.get("end day"));
        governance.invoke(owner, "evaluateVote", id);
        
        return id;
    }

    protected BigInteger createVoteWith(String name, BigInteger totalSupply, BigInteger forVotes, BigInteger againstVotes) {
        Account forVoter = sm.createAccount();
        Account aginstVoter = sm.createAccount();

        BigInteger id = defineTestVoteWithName(name);
        Map<String, Object> vote = getVote(id);

        when(baln.mock.totalSupply()).thenReturn(totalSupply);
        when(baln.mock.stakedBalanceOfAt(eq(forVoter.getAddress()), any(BigInteger.class))).thenReturn(forVotes);
        when(baln.mock.stakedBalanceOfAt(eq(aginstVoter.getAddress()), any(BigInteger.class))).thenReturn(againstVotes);
   
        goToDay((BigInteger)vote.get("start day"));
      
        //Act
        governance.invoke(forVoter, "castVote", id, true);
        governance.invoke(aginstVoter, "castVote", id, false);

        return id;
    }

    protected BigInteger defineTestVote() {
        return defineTestVoteWithName("test");
    }

    protected BigInteger defineTestVoteWithName(String name) {
        sm.getBlock().increase(DAY);
        BigInteger day = (BigInteger) governance.call("getDay");
        String description = "test vote";
        String actions = "[]";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);

        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN.multiply(ICX));
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.ONE.multiply(ICX));
        
        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);

        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(ICX));
        when(dex.mock.totalBalnAt(BALNBNUSD_ID, snapshot, false)).thenReturn(BigInteger.TWO.multiply(ICX));
        when(dex.mock.totalBalnAt(BALNSICX_ID, snapshot, false)).thenReturn(BigInteger.TWO.multiply(ICX));
        return id;
    }

    protected Map<String, Object> getVote(BigInteger id) {
        return (Map<String, Object>) governance.call("checkVote" , id);
    }

    protected void goToDay(BigInteger targetDay) {
        BigInteger day = (BigInteger) governance.call("getDay");
        BigInteger diff = targetDay.subtract(day);
        sm.getBlock().increase(DAY*diff.intValue());
    }

    protected void setup() throws Exception {
        loans = new MockContract<LoansScoreInterface>(LoansScoreInterface.class, sm, owner);
        dex = new MockContract<DexScoreInterface>(DexScoreInterface.class, sm, owner);
        staking = new MockContract<StakingScoreInterface>(StakingScoreInterface.class, sm, owner);
        rewards = new MockContract<RewardsScoreInterface>(RewardsScoreInterface.class, sm, owner);
        reserve = new MockContract<ReserveScoreInterface>(ReserveScoreInterface.class, sm, owner);
        dividends = new MockContract<DividendsScoreInterface>(DividendsScoreInterface.class, sm, owner); 
        daofund = new MockContract<DAOfundScoreInterface>(DAOfundScoreInterface.class, sm, owner); 
        sicx = new MockContract<SicxScoreInterface>(SicxScoreInterface.class, sm, owner);
        bnUSD  = new MockContract<BalancedDollarScoreInterface>(BalancedDollarScoreInterface.class, sm, owner); 
        baln = new MockContract<BalnScoreInterface>(BalnScoreInterface.class, sm, owner);
        bwt = new MockContract<WorkerTokenScoreInterface>(WorkerTokenScoreInterface.class, sm, owner);
        router = new MockContract<RouterScoreInterface>(RouterScoreInterface.class, sm, owner); 
        rebalancing = new MockContract<RebalancingScoreInterface>(RebalancingScoreInterface.class, sm, owner);
        feehandler = new MockContract<FeehandlerScoreInterface>(FeehandlerScoreInterface.class, sm, owner);
        stakedLp = new MockContract<StakedLpScoreInterface>(StakedLpScoreInterface.class, sm, owner);
        governance = sm.deploy(owner, GovernanceImpl.class);

        setupAddresses();
        governance.invoke(owner, "setBalnVoteDefinitionCriterion", BigInteger.valueOf(100)); //1%
        governance.invoke(owner, "setVoteDefinitionFee", ICX); //1% 
        governance.invoke(owner, "setQuorum", BigInteger.ONE);
        governance.invoke(owner, "setVoteDuration", BigInteger.TWO);
        governance.invoke(owner, "setVoteDuration", BigInteger.TWO);
    }





}
