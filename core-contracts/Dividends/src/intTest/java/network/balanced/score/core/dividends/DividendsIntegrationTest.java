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

package network.balanced.score.core.dividends;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.dummyConsumer;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

public class DividendsIntegrationTest {
    static BalancedClient owner;
    static Balanced balanced;
    static BalancedClient tester;
    static BalancedClient tester2;

    @ScoreClient
    static Dividends dividends;

    @ScoreClient
    static Loans loans;

    @ScoreClient
    static Dex dex;

    @ScoreClient
    static BalancedDollar bnusd;

    @ScoreClient
    static Staking staking;

    @ScoreClient
    static Sicx sicx;

    @ScoreClient
    static Baln baln;

    @ScoreClient
    static Governance governance;



    @ScoreClient
    static Rewards rewards;

    @BeforeEach
    void setup() throws Exception {
        System.setProperty("Dividends", System.getProperty("java"));

        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;

        dividends = new DividendsScoreClient(balanced.dividends);
        loans = new LoansScoreClient(balanced.loans);
        bnusd = new BalancedDollarScoreClient(balanced.bnusd);
        staking = new StakingScoreClient(balanced.staking);
        sicx = new SicxScoreClient(balanced.sicx);
        dex = new DexScoreClient(balanced.dex);
        rewards = new RewardsScoreClient(balanced.rewards);
        baln = new BalnScoreClient(balanced.baln);
        governance = new GovernanceScoreClient(balanced.governance);

        tester = balanced.newClient();
        tester2 = balanced.newClient();
    }

    public void activateDividends(){
        governance.setAdmin(balanced.dividends._address(), owner.getAddress());
        dividends.setDistributionActivationStatus(true);
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
    }

    @Test
    void testName(){
        activateDividends();
        assertEquals("Balanced Dividends", dividends.name());
    }

    public BigInteger calculateDividends(BigInteger currentDay, BigInteger fee){
        List<BigInteger> poolList = new ArrayList<>();
        poolList.add(BigInteger.valueOf(3));
        poolList.add(BigInteger.valueOf(4));


        BigInteger myBalnFromPools = BigInteger.ZERO;
        BigInteger totalBalnFromPools = BigInteger.ZERO;

        for (BigInteger poolId : poolList) {
            BigInteger myLp = dex.balanceOfAt(Address.fromString(owner.getAddress().toString()), poolId, currentDay, true);
            BigInteger totalLp = dex.totalSupplyAt(poolId, currentDay, true);
            BigInteger totalBaln = dex.totalBalnAt(poolId, currentDay, true);
            BigInteger equivalentBaln = BigInteger.ZERO;

            if (myLp.compareTo(BigInteger.ZERO) > 0 && totalLp.compareTo(BigInteger.ZERO) > 0 && totalBaln.compareTo(BigInteger.ZERO) > 0) {
                equivalentBaln = (myLp.multiply(totalBaln)).divide(totalLp);
            }

            myBalnFromPools = myBalnFromPools.add(equivalentBaln);
            totalBalnFromPools = totalBalnFromPools.add(totalBaln);

        }

        BigInteger stakedBaln = baln.stakedBalanceOfAt(Address.fromString(owner.getAddress().toString()), currentDay);
        BigInteger totalStakedBaln = baln.totalStakedBalanceOfAt(currentDay);

        BigInteger myTotalBalnToken = stakedBaln.add(myBalnFromPools);
        BigInteger totalBalnToken = totalStakedBaln.add(totalBalnFromPools);

        BigInteger dividends = BigInteger.ZERO;
        Map<String, BigInteger> myDividends = new HashMap<>();
        if (myTotalBalnToken.compareTo(BigInteger.ZERO) > 0 && totalBalnToken.compareTo(BigInteger.ZERO) > 0) {
            BigInteger numerator = myTotalBalnToken.multiply(BigInteger.valueOf(600000000000000000L)).
                    multiply(fee);
            BigInteger denominator = totalBalnToken.multiply(EXA);

            dividends = numerator.divide(denominator);
        }
        return dividends;
    }
    @Test
    void testUserDividends() {
        activateDividends();
        baln.toggleEnableSnapshot();

        BigInteger oldDividendsBalance = dividends.getBalances().get("bnUSD");
        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        BigInteger originationFees = BigInteger.valueOf(100);
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> balances = Map.of("bnUSD", dividendsBalance.add(oldDividendsBalance), "ICX", BigInteger.ZERO);

        // verify the getBalances of dividends contract
        assertEquals(balances, dividends.getBalances());

        balanced.increaseDay(1);
//        fee = self._origination_fee.get() * _amount // POINTS

        for (int i = 0; i < 10; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
        }

        // Claim rewards for that user
        rewards.claimRewards();
        BigInteger balance = baln.balanceOf(Address.fromString(owner.getAddress().toString()));
        // stakes balance token
        baln.stake(balance);

        balanced.increaseDay(1);

        // take loans to create a dividends
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger currentDay = dividends.getDay();

        balanced.increaseDay(1);

        for (int i = 0; i<3; i++) {
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }

        // user dividends ready to  claim
        Map<String, BigInteger> userOldDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

        // bnusd ready to claim
        BigInteger bnusdToClaim = userOldDividends.get(balanced.bnusd._address().toString());

        // previous bnusd balance of a user
        BigInteger userOldBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // dividends result should not be empty
        assertFalse(userOldDividends.isEmpty());

        assertEquals(userOldDividends.get(balanced.bnusd._address().toString()), calculateDividends(currentDay,
                dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString())));

        Map<String, BigInteger> oldBalance = dividends.getBalances();
        BigInteger bnusdAtDividends = oldBalance.get("bnUSD");

        // dividends claimed by user
        dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);

        Map<String, BigInteger> userNewDividends =
                dividends.getUserDividends(Address.fromString(owner.getAddress().toString()),
                        currentDay.intValue(), currentDay.intValue() + 1);

        // once claimed previous userDividends should be empty
        assertTrue(userNewDividends.isEmpty());


        BigInteger userNewBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        // verify new balance of user as dividends are added on the balance too
        assertEquals(userNewBalance, userOldBalance.add(bnusdToClaim));

        Map<String, BigInteger> newBalance = dividends.getBalances();
        BigInteger newBnusdAtDividends = newBalance.get("bnUSD");

        // contract balances should be decreased after claim
        assertEquals(newBnusdAtDividends, bnusdAtDividends.subtract(bnusdToClaim));

    }

    @Test
    void testClaimDividends() {

        activateDividends();
        baln.toggleEnableSnapshot();

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        BigInteger originationFees = BigInteger.valueOf(100);
        // take loans
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger dividendsOldBalance = bnusd.balanceOf(balanced.dividends._address());
        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));

        // test if the dividends is arrived at dividends contract
        assertEquals(dividendsBalance, bnusd.balanceOf(balanced.dividends._address()).subtract(dividendsOldBalance));

        balanced.increaseDay(1);

        for (int i = 0; i < 10; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
        }

        rewards.claimRewards();
        BigInteger balance = baln.balanceOf(Address.fromString(owner.getAddress().toString()));

        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        ScoreIntegrationTest.transfer(balanced.dex._address(), collateral);

        ((StakingScoreClient) staking).stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), null
                , null);

        JSONObject data = new JSONObject();
        data.put("method", "_swap_icx");
        BigInteger swappedAmount = BigInteger.valueOf(10).multiply(BigInteger.TEN.pow(18));
        sicx.transfer(balanced.dex._address(), swappedAmount, data.toString().getBytes());
        Map<String, BigInteger> dexFees = dex.getFees();
        BigInteger icxBalnFee = dexFees.get("icx_baln_fee");
        BigInteger sicxBalance = icxBalnFee.multiply(swappedAmount).divide(BigInteger.valueOf(10000));
        assertEquals(sicxBalance, sicx.balanceOf(balanced.dividends._address()));
        baln.stake(balance);

        balanced.increaseDay(1);

        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);


        BigInteger currentDay = dividends.getDay();

        balanced.increaseDay(1);

        for (int i = 0; i < 10; i++) {
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }

        Map<String, BigInteger> firstDividendsInfo = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue() , currentDay.intValue() + 1);

        assertFalse(firstDividendsInfo.isEmpty());
        // calculate the amount to be received by the user
        BigInteger dividendsBnusdFee = firstDividendsInfo.get(balanced.bnusd._address().toString());
        BigInteger dividendsSicxFee = firstDividendsInfo.get(balanced.sicx._address().toString());

        // take old balance of user
        BigInteger oldBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger oldSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        Map<String, BigInteger> oldBalance = dividends.getBalances();

        // total balance of dividends
        BigInteger sicxAtDividends = oldBalance.get("sICX");
        BigInteger bnusdAtDividends = oldBalance.get("bnUSD");

        BigInteger previousBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));

        dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);

        BigInteger newBnusdBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
        BigInteger newSicxBalance = sicx.balanceOf(Address.fromString(owner.getAddress().toString()));

        // test balance of user
        assertEquals(newBnusdBalance, oldBnusdBalance.add(dividendsBnusdFee));
        assertEquals(newSicxBalance, oldSicxBalance.add(dividendsSicxFee));

        Map<String, BigInteger> newDividendsBalance = dividends.getBalances();

        // total balance of dividends
        BigInteger newSicxAtDividends = newDividendsBalance.get("sICX");
        BigInteger newBnusdAtDividends = newDividendsBalance.get("bnUSD");

        // test the balance of dividends
        assertEquals(newSicxAtDividends, sicxAtDividends.subtract(dividendsSicxFee));
        assertEquals(newBnusdAtDividends, bnusdAtDividends.subtract(dividendsBnusdFee));


        // test user dividends at that day again
        Map<String, BigInteger> dividendsAfterClaim =
                dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(),
                        currentDay.intValue() + 1);

        assertTrue(dividendsAfterClaim.isEmpty());

    }

    @Test
    void testDaofundTransfer() {

        activateDividends();

        // test the transfer of dividends to daofund
        baln.toggleEnableSnapshot();

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger currentDay = dividends.getDay();
        balanced.increaseDay(1);

        for (int i = 0; i < 3; i++) {
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }

        // get dividends for daofund
        BigInteger daofundDividends =
                dividends.getDaoFundDividends(currentDay.intValue(), currentDay.intValue() + 1).get(balanced.bnusd._address().toString());

        assertEquals(daofundDividends,
                dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString()).multiply(dividends.dividendsAt(currentDay).get("daofund")).divide(EXA));

        Map<String, BigInteger> dividendsBalances;

        dividendsBalances = dividends.getBalances();
        BigInteger bnusdBalance = dividendsBalances.get("bnUSD");

        dividends.transferDaofundDividends(currentDay.intValue(), currentDay.intValue() + 1);

        // assert daofund balance after transfer
        assertEquals(daofundDividends, bnusd.balanceOf(balanced.daofund._address()));

        dividendsBalances = dividends.getBalances();
        BigInteger newBnusdBalance = dividendsBalances.get("bnUSD");

        // verify new balance of dividends contract
        assertEquals(newBnusdBalance, bnusdBalance.subtract(daofundDividends));

    }

    @Test
    void testChangeInPercentage() {

        governance.setAdmin(balanced.dividends._address(), owner.getAddress());
        dividends.setDistributionActivationStatus(true);

        // verify the change in percentage
        baln.toggleEnableSnapshot();

        DistributionPercentage map = new DistributionPercentage();
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("900000000000000000");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("100000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };

        // set new percentage of categories of dividends
        dividends.setDividendsCategoryPercentage(percentMap);
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        BigInteger currentDay = dividends.getDay();

        balanced.increaseDay(2);
        for (int i =0 ; i< 5; i++){
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }
        BigInteger bnusdFee = dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString());

        BigInteger bnusdAtDividends = dividends.getDaoFundDividends(currentDay.intValue(), currentDay.intValue() + 1).get(balanced.bnusd._address().toString());

        dividends.transferDaofundDividends(currentDay.intValue(), currentDay.intValue() + 1);

        // verify if the daofund contract receives the dividends or not
        assertEquals(bnusdAtDividends, bnusd.balanceOf(balanced.daofund._address()));

        // verify the total bnUSD transferred as per the new percentage or not
        assertEquals(bnusdAtDividends, bnusdFee.divide(BigInteger.TEN));
    }

    @Test
    void testRemoveCategories() {
        // test the removal of categories from dividends
        governance.setAdmin(balanced.dividends._address(), owner.getAddress());
        dividends.setDistributionActivationStatus(true);
        baln.toggleEnableSnapshot();

        DistributionPercentage map = new DistributionPercentage();

        // firstly setting the baln_holders as 0 percentage
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("0");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("1000000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };

        // setting dividends category to 0 for baln_holders at first
        dividends.setDividendsCategoryPercentage(percentMap);

        // removing the categories
        dividends.removeDividendsCategory("baln_holders");
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        List<String> categories;
        categories = dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    void testAddCategories() {
        // add new categories in dividends

        governance.setAdmin(balanced.dividends._address(), owner.getAddress());
        dividends.setDistributionActivationStatus(true);
        dividends.addDividendsCategory("test");
        governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
        List<String> categories;
        categories = dividends.getDividendsCategories();
        assertEquals("test", categories.get(categories.size() - 1));
    }

    // Only possible with data gathered in old dex
    // @Test
    // void testBnusdBalnDividends() {
    //     // verify the user of a baln/bnUSD LP dividends

    //     activateDividends();

    //     baln.toggleEnableSnapshot();


    //     BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
    //     // take loans
    //     BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
    //     ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //     }

    //     JSONObject data = new JSONObject();
    //     data.put("method", "_deposit");

    //     BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));

    //     balanced.increaseDay(1);
    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //         ((DividendsScoreClient) dividends).distribute(dummyConsumer());
    //     }

    //     // claim rewards of loans
    //     rewards.claimRewards();

    //     // provides LP to baln/bnUSD market
    //     baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
    //     bnusd.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());

    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //     }
    //     dex.add(balanced.baln._address(), balanced.bnusd._address(), lpAmount, lpAmount, true);

    //     balanced.increaseDay(1);

    //     // take loans to provide the dividends
    //     ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

    //     BigInteger currentDay = dividends.getDay();

    //     balanced.increaseDay(1);

    //     for (int i = 0; i < 10; i++) {
    //         ((DividendsScoreClient) dividends).distribute(dummyConsumer());
    //     }
    
    //     BigInteger userPreviousBalance = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
    //     Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

    //     // user Dividends should not be empty
    //     assertFalse(userDividends.isEmpty());

    //     assertEquals(userDividends.get(balanced.bnusd._address().toString()), calculateDividends(currentDay, dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString())));

    //     dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);

    //     // after claim dividends should be empty
    //     Map<String, BigInteger> newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

    //     assertTrue(newUserDividends.isEmpty());

    //     // verify if the dividends is claimed or not
    //     assertEquals(userDividends.get(balanced.bnusd._address().toString()), bnusd.balanceOf(Address.fromString(owner.getAddress().toString())).subtract(userPreviousBalance));

    // }

    // Only possible with data gathered in old dex
    // @Test
    // void testSicxBalnDividends() {

    //     activateDividends();
    //     // verify the user of a sicx/baln LP dividends
    //     baln.toggleEnableSnapshot();

    //     BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));

    //     // take loans
    //     ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
    //             , loanAmount, null, null);

    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //     }

    //     // take sicx from staking contract
    //     ((StakingScoreClient) staking).stakeICX(new BigInteger("500").multiply(BigInteger.TEN.pow(18)), null, null);

    //     JSONObject data = new JSONObject();
    //     data.put("method", "_deposit");

    //     BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));

    //     balanced.increaseDay(1);
    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //         ((DividendsScoreClient) dividends).distribute(dummyConsumer());
    //     }
    //     // claim baln rewards
    //     rewards.claimRewards();

    //     for (int i = 0; i < 10; i++) {
    //         ((RewardsScoreClient) rewards).distribute(dummyConsumer());
    //     }

    //     // provides lp to the baln and sicx
    //     baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
    //     sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
    //     dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);

    //     balanced.increaseDay(1);
    //     BigInteger currentDay = dividends.getDay();

    //     // take loans for dividends
    //     ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

    //     balanced.increaseDay(1);

    //     for (int i = 0; i < 10; i++) {
    //         ((DividendsScoreClient) dividends).distribute(dummyConsumer());
    //     }

    //     BigInteger previousBalances = bnusd.balanceOf(Address.fromString(owner.getAddress().toString()));
    //     Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

    //     // verify the user Dividends is not empty
    //     assertFalse(userDividends.isEmpty());

    //     // claim dividends
    //     dividends.claim(currentDay.intValue(), currentDay.intValue() + 1);


    //     // verify if the dividends is claimed or not.
    //     assertEquals(userDividends.get(balanced.bnusd._address().toString()), bnusd.balanceOf(Address.fromString(owner.getAddress().toString())).subtract(previousBalances));

    //     // After claiming userDividends should be empty
    //     Map<String, BigInteger> newUserDividends = dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(), currentDay.intValue() + 1);

    //     assertTrue(newUserDividends.isEmpty());
    // }

    // @Test
    // void testBalnStakeAndLpUsers() {
    //     // test the dividends received by two user i.e. baln stake and lp provider on baln/sicx pool
    //     BigInteger currentDay = stakeAndLp();
    //     BigInteger dailyFees;
    //     dailyFees = dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString());

    //     // getUserDividends for both of user
    //     Map<String, BigInteger> userDividends = dividends.getUserDividends(Address.fromString(tester2.getAddress().toString()), currentDay.intValue() , currentDay.intValue() + 1);
    //     Map<String, BigInteger> userDividendsTester = dividends.getUserDividends(Address.fromString(tester.getAddress().toString()), currentDay.intValue() , currentDay.intValue() + 1);
    //     Map<String, BigInteger> userDividendsGovernance = dividends.getUserDividends(Address.fromString(balanced.governance._address().toString()), currentDay.intValue() , currentDay.intValue() + 1);


    //     // bnusd to be received by each user
    //     BigInteger userDividendsBnusd = userDividends.get(balanced.bnusd._address().toString());
    //     BigInteger userDividendsTesterBnusd = userDividendsTester.get(balanced.bnusd._address().toString());
    //     BigInteger userDividendsGovernanceBnusd = userDividendsGovernance.get(balanced.bnusd._address().toString());

    //     for (int i = 0; i< 4; i ++){
    //         ((DividendsScoreClient) dividends).distribute(dummyConsumer());
    //     }
    //     // Dividends of owner should not be empty
    //     assertFalse(userDividends.isEmpty());
    //     // Dividends of another account also should not be empty
    //     assertFalse(userDividendsTester.isEmpty());
    //     BigInteger actual =userDividendsBnusd.add(userDividendsTesterBnusd).add(userDividendsGovernanceBnusd);
    //     BigInteger expected = dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100));
    //     // sum of both dividends add up to 60 percent of the daily fees of that day
    //     assertTrue(expected.subtract(actual).compareTo(BigInteger.ZERO)  > 0);

    // }

    @Test
    void testContinuousRewards() {

        activateDividends();

        // test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
        // the dividends
        baln.toggleEnableSnapshot();
        baln.setTimeOffset();
        ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        // set continuous rewards day

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        for (int i = 0; i < 10; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
        }
        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        ((StakingScoreClient) staking).stakeICX(amount, Address.fromString(tester.getAddress().toString()), null);
        ((StakingScoreClient) staking).stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");


        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));

        balanced.increaseDay(1);

        for (int i = 0; i < 10; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }
        // claim rewards for the user
        rewards.claimRewards();

        for (int i = 0; i < 10; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
        }

        // provides liquidity to baln/Sicx pool
        baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by tester wallet
        tester.baln.stake(BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18)));

        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        governance.setMarketName(pid, name);

        balanced.increaseDay(1);

        BigInteger currentDay = dividends.getDay();
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        balanced.increaseDay(1);
        for (int i = 0; i < 10; i++) {
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }
        BigInteger dailyFees = dividends.getDailyFees(currentDay).get(balanced.bnusd._address().toString());


        Map<String, BigInteger> userDividends =
                dividends.getUserDividends(Address.fromString(owner.getAddress().toString()), currentDay.intValue(),
                        currentDay.intValue() + 1);

        Map<String, BigInteger> userDividendsTester =
                dividends.getUserDividends(Address.fromString(tester.getAddress().toString()), currentDay.intValue(),
                        currentDay.intValue() + 1);

        BigInteger userDividendsBnusd = userDividends.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        BigInteger userDividendsTesterBnusd = userDividendsTester.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);

        // LP provider should have zero dividends to claim after continuous rewards is activated
        assertEquals(userDividendsBnusd, BigInteger.ZERO);
        // only baln staker should receive dividends
        assertEquals(userDividendsBnusd.add(userDividendsTesterBnusd),
                dailyFees.multiply(BigInteger.valueOf(60)).divide(BigInteger.valueOf(100)));
    }



    BigInteger stakeAndLp(){
        baln.toggleEnableSnapshot();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));

//        // take loans
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD", loanAmount, null, null);

        // takes sICX from staking contract
        ((StakingScoreClient) staking).stakeICX(new BigInteger("500").multiply(BigInteger.TEN.pow(18)), Address.fromString(tester2.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(2);

        for (int i = 0; i < 5; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }

        // Claim rewards
        rewards.claimRewards();

        for (int i = 0; i < 20; i++) {
            ((RewardsScoreClient) rewards).distribute(dummyConsumer());
        }
        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // add liquidity to baln/sICX pool
        tester.baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        tester.staking.stakeICX(lpAmount.multiply(BigInteger.TWO), null, null);
        tester.sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        tester.dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        baln.transfer(Address.fromString(tester.getAddress().toString()),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance through another user
        tester.baln.stake(BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)));

        balanced.increaseDay(1);

        BigInteger currentDay = dividends.getDay();
        // take loans to create dividends
        ((LoansScoreClient) loans).depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        balanced.increaseDay(1);
        for (int i = 0; i < 10; i++) {
            ((DividendsScoreClient) dividends).distribute(dummyConsumer());
        }

        return currentDay;

    }

}
