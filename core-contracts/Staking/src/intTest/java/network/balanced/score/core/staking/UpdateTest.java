package network.balanced.score.core.staking;


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


import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.SicxScoreClient;
import network.balanced.score.lib.interfaces.Staking;
import network.balanced.score.lib.interfaces.StakingScoreClient;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class UpdateTest implements ScoreIntegrationTest {


    // random prep address that is outside of top prep
    private final Address outsideTopPrep = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");


    public static DefaultScoreClient stakingScore;
    public static DefaultScoreClient sicxScore;
    public static DefaultScoreClient sicxScore2;
    public static DefaultScoreClient testerScore;
    public static DefaultScoreClient testerScore2;
    public static String secondUser;
    public static KeyWallet owner;
    public static KeyWallet testerWallet;
    public static KeyWallet testerWallet2;


//
//    private static Balanced balanced;

    @ScoreClient
    private static Staking stakingScoreClient;

    @ScoreClient
    private static Staking testerScoreClient;

    @ScoreClient
    private static Staking testerScoreClient2;


    @ScoreClient
    private static Sicx sicxScoreClient;

    // address generated
    private final Address senderAddress = Address.fromString(owner.getAddress().toString());
    private final Address testerAddress = Address.fromString(testerWallet.getAddress().toString());


    // keep the staking address here
    private final Address stakingAddress = Address.fromString(stakingScore._address().toString());

    @BeforeAll
    static void setup() throws Exception {
        // current mainnet version is deployed
        System.setProperty("Staking", System.getProperty("jar"));
        owner = (KeyWallet) chain.godWallet;

        stakingScore = ScoreIntegrationTest.deploy(owner, "Staking", null);
        sicxScore = ScoreIntegrationTest.deploy(owner, "Sicx", Map.of("_admin", stakingScore._address()));
        stakingScoreClient = new StakingScoreClient(stakingScore);
        sicxScoreClient = new SicxScoreClient(sicxScore);
        stakingScoreClient.toggleStakingOn();
        stakingScoreClient.setSicxAddress(sicxScore._address());
        sicxScoreClient.setStaking(stakingScore._address());
        sicxScoreClient.setMinter(stakingScore._address());

        testerWallet = KeyWallet.create();
        testerWallet2 = KeyWallet.create();
        testerScore = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWallet, stakingScore._address());
        stakingScore._transfer(DefaultScoreClient.address(testerWallet.getAddress().toString()), BigInteger.valueOf(500L).multiply(ONE_EXA), null);
        testerScoreClient = new StakingScoreClient(testerScore);


    }

    void checkUserDelegations(BigInteger prepCount, BigInteger userIcx, Address user) {
        Map<String, BigInteger> addressDelegations = stakingScoreClient.getAddressDelegations(user);
        BigInteger evenlyPrepDistributedCount = BigInteger.ZERO;
        BigInteger evenlyDistributedSum = BigInteger.ZERO;
        for (String prep : addressDelegations.keySet()) {
            evenlyPrepDistributedCount = evenlyPrepDistributedCount.add(BigInteger.ONE);
            evenlyDistributedSum = evenlyDistributedSum.add(addressDelegations.get(prep));
        }
        Assertions.assertEquals(prepCount, evenlyPrepDistributedCount);
        Assertions.assertEquals(userIcx, evenlyDistributedSum);
    }

    void checkPrepDelegations(BigInteger prepCount, BigInteger userIcx) {
        Map<String, BigInteger> addressDelegations = stakingScoreClient.getPrepDelegations();
        BigInteger evenlyPrepDistributedCount = BigInteger.ZERO;
        BigInteger evenlyDistributedSum = BigInteger.ZERO;
        for (String prep : addressDelegations.keySet()) {
            evenlyPrepDistributedCount = evenlyPrepDistributedCount.add(BigInteger.ONE);
            evenlyDistributedSum = evenlyDistributedSum.add(addressDelegations.get(prep));
        }
        Assertions.assertEquals(prepCount, evenlyPrepDistributedCount);
        Assertions.assertEquals(userIcx, evenlyDistributedSum);
    }

    void checkNetworkDelegations(BigInteger prepCount, BigInteger userIcx) {
        Map<String, Object> delegations = systemScore.getDelegation(stakingScore._address());
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");
        BigInteger networkPrepCount = BigInteger.ZERO;
        BigInteger networkDelegationSum = BigInteger.ZERO;
        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkPrepCount = networkPrepCount.add(BigInteger.ONE);
            networkDelegationSum = networkDelegationSum.add(new BigInteger(hexValue, 16));
        }
        Assertions.assertEquals(prepCount, networkPrepCount);
        Assertions.assertEquals(userIcx, networkDelegationSum);
    }

    @Test
    void testStakeAfterUpdate() throws Exception {
        /*
        This test covers two user ,
        1. one user staking without specifying delegation preference,
        then after update of contract the same user stakes and delegates to one prep.
        2. another user staking with specifying delegation preference before update and once the contract is updated
        the user stakes again
         */
        BigInteger toStake = new BigInteger("100").multiply(ONE_EXA);
        // first user stakes ICX
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);
        int previousTopPrepCount = stakingScoreClient.getTopPreps().size();

        // do necessary check on current mainnet version
        checkUserDelegations(BigInteger.valueOf(100L), toStake, senderAddress);
        Assertions.assertEquals(toStake, stakingScoreClient.getTotalStake());

        PrepDelegations p = new PrepDelegations();
        Address toDelegate = stakingScoreClient.getTopPreps().get(1);
        p._address = toDelegate;
        p._votes_in_per = HUNDRED_PERCENTAGE;
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};

        // second user delegates to one address and then stake
        testerScoreClient.delegate(userDelegation);
        ((StakingScoreClient) testerScoreClient).stakeICX(toStake, null
                , null);

        Map<String, BigInteger> actualPrepDelegations = new HashMap<>();
        Map<String, BigInteger> userDelegationPercentage = new HashMap<>();

        // do necessary check for second user
        actualPrepDelegations.put(toDelegate.toString(), toStake);
        userDelegationPercentage.put(String.valueOf(toDelegate), HUNDRED_PERCENTAGE);
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualUserDelegationPercentage(testerAddress));
        checkUserDelegations(BigInteger.ONE, toStake, testerAddress);

        // contract is updated here
        ((StakingScoreClient) stakingScoreClient)._update(System.getProperty("java"), null);

        // After update the second user delegation should not be changed and
        // also getActualPrepDelegations keyset will not be empty
        actualPrepDelegations.put(toDelegate.toString(), toStake);
        userDelegationPercentage.put(String.valueOf(toDelegate), HUNDRED_PERCENTAGE);
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(testerAddress));
        checkUserDelegations(BigInteger.ONE, toStake, testerAddress);

        int newTopPrepCount = stakingScoreClient.getTopPreps().size();
        // top prep count will be less than 100
        Assertions.assertTrue(newTopPrepCount < previousTopPrepCount);
        userDelegationPercentage = new HashMap<>();

        // test user delegation of first user and also test getPrepDelegations
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.valueOf(newTopPrepCount), toStake, senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.TWO));
        // as there is no any tx done after update the network delegations remains unchanged
        checkNetworkDelegations(BigInteger.valueOf(100L), toStake.multiply(BigInteger.TWO));
        Assertions.assertEquals(toStake.multiply(BigInteger.TWO), stakingScoreClient.getTotalStake());

        // first user stakes some ICX again
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);

        // do all the necessary checks
        Assertions.assertEquals(toStake.multiply(BigInteger.valueOf(3L)), stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.TWO), senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.valueOf(3L)));
        checkNetworkDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.valueOf(3L)));


        Address toDelegateSecond = stakingScoreClient.getTopPreps().get(2);
        p._address = toDelegateSecond;
        p._votes_in_per = HUNDRED_PERCENTAGE;

        userDelegation = new PrepDelegations[]{p};
        // first user delegates to one address
        stakingScoreClient.delegate(userDelegation);

        actualPrepDelegations.put(String.valueOf(toDelegateSecond), toStake.multiply(BigInteger.TWO));
        userDelegationPercentage.put(String.valueOf(toDelegateSecond), HUNDRED_PERCENTAGE);

        // test if after update of contract the delegate function is working as expected or not.
        Assertions.assertEquals(toStake.multiply(BigInteger.valueOf(3L)), stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.ONE, toStake.multiply(BigInteger.TWO), senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.valueOf(3L)));
        checkNetworkDelegations(BigInteger.TWO, toStake.multiply(BigInteger.valueOf(3L)));

        // second user stakes some ICX
        ((StakingScoreClient) testerScoreClient).stakeICX(toStake, null
                , null);

        actualPrepDelegations.put(String.valueOf(toDelegate), toStake.multiply(BigInteger.TWO));
        userDelegationPercentage = new HashMap<>();
        userDelegationPercentage.put(String.valueOf(toDelegate), HUNDRED_PERCENTAGE);


        // test if the second user tx after update works as expected or not.
        Assertions.assertEquals(toStake.multiply(BigInteger.valueOf(4L)), stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(testerAddress));
        checkUserDelegations(BigInteger.ONE, toStake.multiply(BigInteger.TWO), senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.multiply(BigInteger.valueOf(4L)));
        // since each user have already delegated , so there are only 2 preps getting delegation from staking contract
        checkNetworkDelegations(BigInteger.TWO, toStake.multiply(BigInteger.valueOf(4L)));
    }

    @Test
    void testDelegateAfterUpdate() throws Exception {
        /*
        This test covers one user ,
        delegating and staking preps outside of top preps and
        then after update of contract the same user stakes and delegates to the prep inside of top Prep,
        and transfer some sICX to another user.
         */
        BigInteger toStake = new BigInteger("100").multiply(ONE_EXA);

        // delegating to prep outside of top preps and then stake some ICX
        PrepDelegations p = new PrepDelegations();
        Address toDelegate = outsideTopPrep;
        p._address = toDelegate;
        p._votes_in_per = HUNDRED_PERCENTAGE;
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        stakingScoreClient.delegate(userDelegation);
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);

        Map<String, BigInteger> actualPrepDelegations = new HashMap<>();
        Map<String, BigInteger> userDelegationPercentage = new HashMap<>();

        // do necessary check for the user
        actualPrepDelegations.put(toDelegate.toString(), toStake);
        userDelegationPercentage.put(String.valueOf(toDelegate), HUNDRED_PERCENTAGE);
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.ONE, toStake, senderAddress);
        // since there is one prep outside of top preps, prep delegation will have topPrepCount + 1 preps in dict.
        checkPrepDelegations(BigInteger.valueOf(stakingScoreClient.getTopPreps().size()).add(BigInteger.ONE), toStake);
        checkNetworkDelegations(BigInteger.valueOf(stakingScoreClient.getTopPreps().size()), toStake);

        int oldTopPrepCount = stakingScoreClient.getTopPreps().size();

        // contract is updated here
        ((StakingScoreClient) stakingScoreClient)._update(System.getProperty("java"), null);

        int newTopPrepCount = stakingScoreClient.getTopPreps().size();

        // do necessary check after the update
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.ONE, toStake, senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount).add(BigInteger.ONE), toStake);
        // since there is no tx done after update network delegations remains unchanged
        checkNetworkDelegations(BigInteger.valueOf(oldTopPrepCount), toStake);
        Assertions.assertTrue(newTopPrepCount < oldTopPrepCount);

        // first user stakes some ICX again
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);

        BigInteger newTotalStake = toStake.multiply(BigInteger.valueOf(2L));
        actualPrepDelegations.put(toDelegate.toString(), newTotalStake);

        // do all the necessary checks
        Assertions.assertEquals(newTotalStake, stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.ONE, newTotalStake, senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount).add(BigInteger.ONE), newTotalStake);
        checkNetworkDelegations(BigInteger.valueOf(newTopPrepCount), newTotalStake);

        // user delegates to the preps inside top preps
        Address toDelegateSecond = stakingScoreClient.getTopPreps().get(2);
        p._address = toDelegateSecond;
        p._votes_in_per = HUNDRED_PERCENTAGE;
        userDelegation = new PrepDelegations[]{p};
        stakingScoreClient.delegate(userDelegation);

        actualPrepDelegations = new HashMap<>();
        userDelegationPercentage = new HashMap<>();
        actualPrepDelegations.put(String.valueOf(toDelegateSecond), newTotalStake);
        userDelegationPercentage.put(String.valueOf(toDelegateSecond), HUNDRED_PERCENTAGE);

        // do all the necessary checks
        Assertions.assertEquals(newTotalStake, stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.ONE, newTotalStake, senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), newTotalStake);
        checkNetworkDelegations(BigInteger.ONE, newTotalStake);

        // transfer some sICX to another user
        sicxScoreClient.transfer(testerAddress, toStake, null);

        actualPrepDelegations = new HashMap<>();
        actualPrepDelegations.put(String.valueOf(toDelegateSecond), toStake);

        // do all the necessary checks
        Assertions.assertEquals(newTotalStake, stakingScoreClient.getTotalStake());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        userDelegationPercentage = new HashMap<>();
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(testerAddress));
        checkUserDelegations(BigInteger.ONE, toStake, senderAddress);
        checkUserDelegations(BigInteger.valueOf(newTopPrepCount), toStake, testerAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), newTotalStake);
        checkNetworkDelegations(BigInteger.valueOf(newTopPrepCount), newTotalStake);

    }

    @Test
    void testUnstakeUpdate() throws Exception {
        /*
        This test covers one user ,
        unstaking some sICX before update and staking some ICX once the contract is updated.
         */
        BigInteger toStake = new BigInteger("100").multiply(ONE_EXA);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        BigInteger toTransfer = toStake.divide(BigInteger.TWO);

        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);

        sicxScoreClient.transfer(stakingAddress, toTransfer, data.toString().getBytes());

        List<Map<String, Object>> userUnstakeInfo = stakingScoreClient.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(toTransfer, new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = stakingScoreClient.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        Map<String, BigInteger> actualPrepDelegations = new HashMap<>();
        Map<String, BigInteger> userDelegationPercentage = new HashMap<>();

        int oldTopPrepCount = stakingScoreClient.getTopPreps().size();

        // contract is updated here
        ((StakingScoreClient) stakingScoreClient)._update(System.getProperty("java"), null);


        userUnstakeInfo = stakingScoreClient.getUserUnstakeInfo(senderAddress);

        assertEquals(toTransfer, stakingScoreClient.getUnstakingAmount());
        assertEquals(toTransfer, stakingScoreClient.getTotalStake());
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(toTransfer, new BigInteger(hexValue, 16));

        unstakeInfo = stakingScoreClient.getUnstakeInfo();
        firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        actualPrepDelegations = new HashMap<>();
        userDelegationPercentage = new HashMap<>();

        int newTopPrepCount = stakingScoreClient.getTopPreps().size();

        // do necessary check after the update
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(actualPrepDelegations, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.valueOf(newTopPrepCount), toStake.subtract(toTransfer), senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.subtract(toTransfer));
        // since there is no tx done after update, network delegations remains unchanged
        checkNetworkDelegations(BigInteger.valueOf(oldTopPrepCount), toStake.subtract(toTransfer));
        Assertions.assertTrue(newTopPrepCount < oldTopPrepCount);

        BigInteger amount = new BigInteger("30").multiply(ONE_EXA);
        // stakes 30 ICX again
        ((StakingScoreClient) stakingScoreClient).stakeICX(amount, null
                , null);

        assertEquals(toTransfer.subtract(amount), stakingScoreClient.getUnstakingAmount());
        assertEquals(toTransfer.add(amount), stakingScoreClient.getTotalStake());

        userUnstakeInfo = stakingScoreClient.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(toTransfer.subtract(amount), new BigInteger(hexValue, 16));

        unstakeInfo = stakingScoreClient.getUnstakeInfo();
        firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        actualPrepDelegations = new HashMap<>();
        userDelegationPercentage = new HashMap<>();

        assertEquals(actualPrepDelegations, stakingScoreClient.getActualPrepDelegations());
        assertEquals(userDelegationPercentage, stakingScoreClient.getActualUserDelegationPercentage(senderAddress));
        checkUserDelegations(BigInteger.valueOf(newTopPrepCount), toStake.subtract(toTransfer).add(amount), senderAddress);
        checkPrepDelegations(BigInteger.valueOf(newTopPrepCount), toStake.subtract(toTransfer).add(amount));
        checkNetworkDelegations(BigInteger.valueOf(newTopPrepCount), toStake.subtract(toTransfer).add(amount));


    }
}
