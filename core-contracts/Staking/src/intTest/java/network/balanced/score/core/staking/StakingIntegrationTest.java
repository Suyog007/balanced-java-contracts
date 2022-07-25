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

package network.balanced.score.core.staking;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.simple.JSONObject;

import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;
import org.json.simple.parser.*;
import scorex.util.HashMap;

import java.io.*;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StakingIntegrationTest implements ScoreIntegrationTest {

    // random prep address that is outside of top prep
    private final Address outsideTopPrep = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");


    public static DefaultScoreClient stakingScore;
    public static DefaultScoreClient sicxScore;
    public static DefaultScoreClient sicxScoreSecond;
    public static DefaultScoreClient testerScore;
    public static DefaultScoreClient testerScoreSecond;
    public static String secondUser;
    public static KeyWallet owner;
    public static KeyWallet testerWallet;
    public static KeyWallet testerWalletSecond;


//
//    private static Balanced balanced;

    @ScoreClient
    private static Staking stakingScoreClient;

    @ScoreClient
    private static Staking testerScoreClient;

    @ScoreClient
    private static Staking testerScoreClientSecond;


    @ScoreClient
    private static Sicx sicxScoreClient;

    @ScoreClient
    private static Sicx sicxScoreClientSecond;

    // address generated
    private final Address senderAddress = Address.fromString(owner.getAddress().toString());


    // keep the staking address here
    private final Address stakingAddress = Address.fromString(stakingScore._address().toString());


    @BeforeAll
    static void setup() throws IOException, ParseException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        owner = (KeyWallet) chain.godWallet;

        JSONParser parser = new JSONParser();
        File f = new File("contract_address.json");
        if (f.exists()) {
            JSONObject data = (JSONObject) parser.parse(
                    new FileReader("contract_address.json"));
            String stakingAddress = (String) data.get("Staking");
            String sicxAddress = (String) data.get("Sicx");
            secondUser = (String) data.get("secondUser");
            String testerWalletPrivateKey = (String) data.get("testerWalletPrivateKey");
            String testerWallet2PrivateKey = (String) data.get("testerWallet2PrivateKey");
            testerWallet = KeyWallet.load(new Bytes(testerWalletPrivateKey));
            testerWalletSecond = KeyWallet.load(new Bytes(testerWallet2PrivateKey));
            stakingScore = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, owner, DefaultScoreClient.address(stakingAddress));
            sicxScore = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, owner, DefaultScoreClient.address(sicxAddress));
            sicxScoreSecond = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWalletSecond, DefaultScoreClient.address(sicxAddress));
            testerScore = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWallet, DefaultScoreClient.address(stakingAddress));
            testerScoreSecond = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWalletSecond, DefaultScoreClient.address(stakingAddress));
            stakingScoreClient = new StakingScoreClient(stakingScore);
            testerScoreClient = new StakingScoreClient(testerScore);
            testerScoreClientSecond = new StakingScoreClient(testerScoreSecond);
            sicxScoreClient = new SicxScoreClient(sicxScore);
            sicxScoreClientSecond = new SicxScoreClient(sicxScoreSecond);
        } else {
            stakingScore = ScoreIntegrationTest.deploy(owner, "Staking", null);
            sicxScore = ScoreIntegrationTest.deploy(owner, "Sicx", Map.of("_admin", stakingScore._address()));
            JSONObject json = new JSONObject();
            json.put("Staking", stakingScore._address().toString());
            json.put("Sicx", sicxScore._address().toString());
            KeyWallet wallet = KeyWallet.create();
            testerWallet = KeyWallet.create();
            testerWalletSecond = KeyWallet.create();
            foundation.icon.jsonrpc.Address newAddress = DefaultScoreClient.address(wallet.getAddress().toString());
            json.put("secondUser", newAddress.toString());
            json.put("testerWalletPrivateKey", testerWallet.getPrivateKey().toString());
            json.put("testerWallet2PrivateKey", testerWalletSecond.getPrivateKey().toString());
            PrintWriter writer = new PrintWriter("contract_address.json", "UTF-8");
            writer.write(json.toString());
            writer.close();
            stakingScoreClient = new StakingScoreClient(stakingScore);
            sicxScoreClient = new SicxScoreClient(sicxScore);
            sicxScoreSecond = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWalletSecond, sicxScore._address());
            sicxScoreClientSecond = new SicxScoreClient(sicxScoreSecond);
            testerScore = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWallet, stakingScore._address());
            testerScoreSecond = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testerWalletSecond, stakingScore._address());
            testerScoreClient = new StakingScoreClient(testerScore);
            testerScoreClientSecond = new StakingScoreClient(testerScoreSecond);
            stakingScore._transfer(DefaultScoreClient.address(testerWallet.getAddress().toString()), BigInteger.valueOf(500L).multiply(ONE_EXA), null);
            stakingScore._transfer(DefaultScoreClient.address(testerWalletSecond.getAddress().toString()), BigInteger.valueOf(500L).multiply(ONE_EXA), null);
            secondUser = newAddress.toString();
            stakingScoreClient.toggleStakingOn();
            stakingScoreClient.setSicxAddress(sicxScore._address());
            sicxScoreClient.setStaking(stakingScore._address());
            sicxScoreClient.setMinter(stakingScore._address());

        }

    }

    private final Address userSecond = Address.fromString(secondUser);
    private final Address testerAddress = Address.fromString(testerWallet.getAddress().toString());
    private final Address testerAddressSecond = Address.fromString(testerWalletSecond.getAddress().toString());

    @Test
    @Order(1)
    void testName() {
        assertEquals("Staked ICX Manager", stakingScoreClient.name());
    }

    @Test
    @Order(2)
    void testSicxAddress() throws Exception {
        Address value = stakingScoreClient.getSicxAddress();
        assertEquals(sicxScore._address(), value);
    }

    @Test
    @Order(3)
    void checkTopPreps() {
        List<Address> topPrep = stakingScoreClient.getTopPreps();
        List<Address> prepList = stakingScoreClient.getPrepList();
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();

        BigInteger sum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            sum = sum.add(value);
        }

        // finding top preps having productivity greater than 90 and bondedAmountmore than 0
        Map<String, Object> prepDict = systemScore.getPReps(BigInteger.ONE, BigInteger.valueOf(200));
        List<Map<String, Object>> prepDetails = (List<Map<String, Object>>) prepDict.get("preps");
        List<Address> expectedTopPreps = new ArrayList<>();
        for (Map<String, Object> preps : prepDetails) {
            Address prepAddress = Address.fromString((String) preps.get("address"));
            Map<String, Object> singlePrepInfo = systemScore.getPRep(prepAddress);
            BigInteger totalBlocks = new BigInteger(singlePrepInfo.get("totalBlocks").toString().substring(2), 16);
            BigInteger validatedBlocks = new BigInteger(singlePrepInfo.get("validatedBlocks").toString().substring(2), 16);
            validatedBlocks = validatedBlocks.multiply(ONE_EXA);
            totalBlocks = totalBlocks.multiply(ONE_EXA);
            if (totalBlocks.compareTo(BigInteger.ZERO) == 0) {
                continue;
            }
            BigInteger prepProductivity = validatedBlocks.multiply(ONE_EXA).multiply(HUNDRED).divide(totalBlocks);
            BigInteger bondedAmount = new BigInteger(singlePrepInfo.get("bonded").toString().substring(2), 16);
            if ((!bondedAmount.equals(BigInteger.ZERO)) && prepProductivity.compareTo(BigInteger.valueOf(90L)) > 0) {
                expectedTopPreps.add(prepAddress);
            }
        }

        assertEquals(expectedTopPreps.size(), topPrep.size());
        assertEquals(new BigInteger("0"), sum);
        assertEquals(expectedTopPreps.size(), prepList.size());
    }

    @Test
    @Order(4)
    void testStakeIcxByNewUser() {
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        ((StakingScoreClient) stakingScoreClient).stakeICX(new BigInteger("8").multiply(ONE_EXA), null
                , null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), ONE_EXA);
                expectedNetworkDelegations.put(prep.toString(), ONE_EXA);
                expectedPrepDelegations.put(prep.toString(), ONE_EXA);
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("8").multiply(ONE_EXA)),
                stakingScoreClient.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("8").multiply(ONE_EXA)),
                sicxScoreClient.totalSupply());
        assertEquals(userBalance.add(new BigInteger("8").multiply(ONE_EXA)),
                sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    @Order(5)
    void testSecondStakeIcx() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScoreClient.balanceOf(userSecond);

        // stakes 200 ICX to user2
        ((StakingScoreClient) stakingScoreClient).stakeICX(new BigInteger("16").multiply(ONE_EXA),
                userSecond, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(userSecond);

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("16").multiply(ONE_EXA)),
                stakingScoreClient.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("16").multiply(ONE_EXA)),
                sicxScoreClient.totalSupply());
        assertEquals(userBalance, sicxScoreClient.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("16").multiply(ONE_EXA)),
                sicxScoreClient.balanceOf(userSecond));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    @Order(6)
    void delegate() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);

        PrepDelegations p = new PrepDelegations();
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(2);
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        // delegates to one address
        stakingScoreClient.delegate(userDelegation);

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(userBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(), userBalance.add(toDistributeEvenly));
                    userExpectedDelegations.put(prep.toString(), userBalance);
                    expectedNetworkDelegations.put(prep.toString(), userBalance.add(toDistributeEvenly));
                } else {
                    expectedNetworkDelegations.put(prep.toString(), toDistributeEvenly);
                    expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
                }
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(currentTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(userBalance, sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    public boolean contains(Address target, List<Address> addresses) {
        for (Address address : addresses) {
            if (address.equals(target)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Order(7)
    void delegateToThreePreps() {
        PrepDelegations p = new PrepDelegations();
        PrepDelegations p2 = new PrepDelegations();
        PrepDelegations p3 = new PrepDelegations();

        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        List<Address> delegatedAddressList = stakingScoreClient.getTopPreps();
        Address firstPrepToVote = delegatedAddressList.get(4);
        Address secondPrepToVote = delegatedAddressList.get(5);
        Address thirdPrepToVote = delegatedAddressList.get(6);

        BigInteger VotingPercentageFirstPrep = BigInteger.valueOf(50L).multiply(ONE_EXA);
        BigInteger VotingPercentageSecondPrep = BigInteger.valueOf(25).multiply(ONE_EXA);
        BigInteger VotingPercentageThirdPrep = BigInteger.valueOf(25).multiply(ONE_EXA);

        p._address = firstPrepToVote;
        p._votes_in_per = VotingPercentageFirstPrep;
        p2._address = secondPrepToVote;
        p2._votes_in_per = VotingPercentageSecondPrep;
        p3._address = thirdPrepToVote;
        p3._votes_in_per = VotingPercentageThirdPrep;
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p, p2, p3
        };

        // delegates to three address
        stakingScoreClient.delegate(userDelegation);

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger voteToFirstPrep = VotingPercentageFirstPrep.divide(HUNDRED).multiply(userBalance).divide(ONE_EXA);
        BigInteger voteToSecondPrep = VotingPercentageSecondPrep.divide(HUNDRED).multiply(userBalance).divide(ONE_EXA);
        BigInteger voteToThirdPrep = VotingPercentageThirdPrep.divide(HUNDRED).multiply(userBalance).divide(ONE_EXA);

        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(userBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(secondPrepToVote.toString())) {

                    expectedPrepDelegations.put(prep.toString(), voteToSecondPrep.add(toDistributeEvenly));
                    userExpectedDelegations.put(prep.toString(), voteToSecondPrep);
                    expectedNetworkDelegations.put(prep.toString(), voteToSecondPrep.add(toDistributeEvenly));
                } else if (prep.toString().equals(thirdPrepToVote.toString())) {
                    expectedPrepDelegations.put(prep.toString(), voteToThirdPrep.add(toDistributeEvenly));
                    userExpectedDelegations.put(prep.toString(), voteToThirdPrep);
                    expectedNetworkDelegations.put(prep.toString(), voteToThirdPrep.add(toDistributeEvenly));
                } else if (prep.toString().equals(firstPrepToVote.toString())) {
                    expectedPrepDelegations.put(prep.toString(), voteToFirstPrep.add(toDistributeEvenly));
                    userExpectedDelegations.put(prep.toString(), voteToFirstPrep);
                    expectedNetworkDelegations.put(prep.toString(), voteToFirstPrep.add(toDistributeEvenly));
                } else {
                    expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
                    expectedNetworkDelegations.put(prep.toString(), toDistributeEvenly);
                }
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);


        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(userBalance, sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);


    }

    @Test
    @Order(8)
    void delegateOutsideTopPrep() {
        PrepDelegations p = new PrepDelegations();

        // random prep address generated
        BigInteger delegatedPercentage = HUNDRED.multiply(ONE_EXA);
        p._address = outsideTopPrep;
        p._votes_in_per = delegatedPercentage;

        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);

        // delegates to one address
        stakingScoreClient.delegate(userDelegation);

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(userBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
                userExpectedDelegations.put(prep.toString(), userBalance);
            }
            if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), currentTotalStake.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);

            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(userBalance, sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(9)
    void stakeAfterDelegate() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);

        BigInteger toStake = new BigInteger("8").multiply(ONE_EXA);

        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake, null
                , null);

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();

        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(userBalance.add(toStake));
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance.add(toStake));
                userExpectedDelegations.put(prep.toString(), userBalance.add(toStake));
            }
            if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), currentTotalStake.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);

        BigInteger newSupply = previousTotalStake.add(toStake);
        assertEquals(newSupply, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(newSupply, currentTotalStake);
        assertEquals(previousTotalSupply.add(toStake), sicxScoreClient.totalSupply());
        assertEquals(userBalance.add(toStake), sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    void checkNetworkDelegations(Map<String, BigInteger> expected) {
        Map<String, Object> delegations = systemScore.getDelegation(stakingScore._address());
        Map<String, BigInteger> networkDelegations = new java.util.HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(expected, networkDelegations);

    }

    @Test
    @Order(10)
    void transferPreferenceToNoPreference() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScoreClient.balanceOf(userSecond);
        BigInteger sicxToTransfer = new BigInteger("8").multiply(ONE_EXA);
        sicxScoreClient.transfer(userSecond, sicxToTransfer, null);
        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(sicxToTransfer);
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(newUserBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations.put(prep.toString(), newUserBalance);
            }
            if (contains(prep, topPreps)) {
                user2ExpectedDelegations.put(prep.toString(), toDistributeEvenly);
                expectedNetworkDelegations.put(prep.toString(), currentTotalStake.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingScoreClient.getAddressDelegations(userSecond);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(newUserBalance,
                sicxScoreClient.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(sicxToTransfer),
                sicxScoreClient.balanceOf(userSecond));

        checkNetworkDelegations(expectedNetworkDelegations);

    }


    @Test
    @Order(11)
    void delegateFirstThenStake() {
        PrepDelegations p = new PrepDelegations();
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);

        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);

        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);

        // delegates to one address
        testerScoreClient.delegate(userDelegation);
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(testerAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(delegatedAddress.toString(), BigInteger.ZERO);
        assertEquals(userDelegations, userExpectedDelegations);
        BigInteger toStake = new BigInteger("4").multiply(ONE_EXA);
        ((StakingScoreClient) testerScoreClient).stakeICX(toStake, null, null);
        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        userDelegations.clear();
        userDelegations = stakingScoreClient.getAddressDelegations(testerAddress);

        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(userBalance).subtract(toStake);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(toStake);


        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(toStake).add(toDistributeEvenly));
                userExpectedDelegations.put(prep.toString(), testerBalance.add(toStake));
                expectedNetworkDelegations.put(prep.toString(), testerBalance.add(toStake).add(toDistributeEvenlyNetwork.divide(topPrepSize)));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(toStake), currentTotalStake);
        assertEquals(previousTotalSupply.add(toStake), sicxScoreClient.totalSupply());
        assertEquals(testerBalance.add(toStake),
                sicxScoreClient.balanceOf(testerAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }


    @Test
    @Order(12)
    void transferPreferenceToPreference() {
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);

        BigInteger toTransfer = new BigInteger("4").multiply(ONE_EXA);
        sicxScoreClient.transfer(testerAddress, toTransfer, null);


        BigInteger newTesterBalance = sicxScoreClient.balanceOf(testerAddress);

        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();
        System.out.println(prepList);

        BigInteger newUserBalance = userBalance.subtract(new BigInteger("4").multiply(ONE_EXA));
        BigInteger newSupply = testerBalance.add(new BigInteger("4").multiply(ONE_EXA));

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(newUserBalance).subtract(newTesterBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(newTesterBalance);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations.put(prep.toString(), newUserBalance);
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), newTesterBalance.add(toDistributeEvenly));
                user2ExpectedDelegations.put(prep.toString(), newSupply);
                expectedNetworkDelegations.put(prep.toString(), newTesterBalance.add(toDistributeEvenlyNetwork.divide(topPrepSize)));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingScoreClient.getAddressDelegations(testerAddress);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(newUserBalance,
                sicxScoreClient.balanceOf(senderAddress));
        assertEquals(newSupply,
                sicxScoreClient.balanceOf(testerAddress));
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(13)
    void transferNullToNull() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);
        Address receiverAddress = stakingScoreClient.getTopPreps().get(0);

        BigInteger toStake = new BigInteger("8").multiply(ONE_EXA);
        BigInteger toTransfer = new BigInteger("4").multiply(ONE_EXA);

        ((StakingScoreClient) testerScoreClientSecond).stakeICX(toStake, null
                , null);

        sicxScoreClientSecond.transfer(receiverAddress, toTransfer, null);


        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        System.out.println(prepList);


        BigInteger newUserBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger newTesterBalance = sicxScoreClient.balanceOf(testerAddress);
        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(newUserBalance).subtract(newTesterBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(newTesterBalance);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            } else {
                BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(), newTesterBalance.add(toDistributeEvenly));
                    expectedNetworkDelegations.put(prep.toString(), newTesterBalance.add(toDistributeEvenlyNetwork.divide(topPrepSize)));
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                } else if (contains(prep, topPreps)) {
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                    expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                    expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
                }
            }
        }

        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(testerAddressSecond);
        Map<String, BigInteger> userDelegations2 = stakingScoreClient.getAddressDelegations(receiverAddress);
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(userDelegations2, user2ExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(toStake), currentTotalStake);
        assertEquals(previousTotalSupply.add(toStake), sicxScoreClient.totalSupply());
        assertEquals(toTransfer,
                sicxScoreClient.balanceOf(receiverAddress));
        assertEquals(toTransfer,
                sicxScoreClient.balanceOf(testerAddressSecond));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(14)
    void transferNullToPreference() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);
        BigInteger testerBalance2 = sicxScoreClient.balanceOf(testerAddressSecond);
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);

        BigInteger toTransfer = new BigInteger("4").multiply(ONE_EXA);

        sicxScoreClientSecond.transfer(senderAddress, toTransfer, null);

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> userExpectedDelegations2 = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();

        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger newUserBalance = userBalance.add(toTransfer);
        BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(newUserBalance).subtract(testerBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(testerBalance);


        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations2.put(prep.toString(), newUserBalance);

            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenly));
                expectedNetworkDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenlyNetwork.divide(topPrepSize)));
                userExpectedDelegations.put(prep.toString(), divide);
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
                userExpectedDelegations.put(prep.toString(), divide);
            }
        }

        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(testerAddressSecond);
        Map<String, BigInteger> userDelegations2 = stakingScoreClient.getAddressDelegations(senderAddress);
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();

        assertEquals(userDelegations, new java.util.HashMap<>());
        assertEquals(userDelegations2, userExpectedDelegations2);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(previousTotalSupply, sicxScoreClient.totalSupply());
        assertEquals(testerBalance2.subtract(toTransfer),
                sicxScoreClient.balanceOf(testerAddressSecond));
        assertEquals(userBalance.add(toTransfer),
                sicxScoreClient.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    @Order(15)
    void unstakePartial() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");

        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);

        BigInteger toTransfer = new BigInteger("4").multiply(ONE_EXA);
        System.out.println(stakingScoreClient.getPrepList());
        sicxScoreClient.transfer(stakingAddress, toTransfer, data.toString().getBytes());


        BigInteger testerBalance2 = sicxScoreClient.balanceOf(testerAddressSecond);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();


        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(testerBalance).subtract(toTransfer);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(testerBalance);

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance.subtract(toTransfer));
                userExpectedDelegations.put(prep.toString(), userBalance.subtract(toTransfer));
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenly));
                expectedNetworkDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenlyNetwork.divide(topPrepSize)));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(toTransfer),
                stakingScoreClient.getTotalStake());
        assertEquals(toTransfer, stakingScoreClient.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(toTransfer),
                sicxScoreClient.totalSupply());
        assertEquals(userBalance.subtract(toTransfer),
                sicxScoreClient.balanceOf(senderAddress));


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
        assertEquals(previousTotalStake.subtract(toTransfer),
                prepDelegationsSum);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

        Map<String, Object> stakeDetails = systemScore.getStake(stakingAddress);
        List<Map<String, Object>> unstakeInfo2 = (List<Map<String, Object>>) stakeDetails.get("unstakes");
        String unstakeExpected = (String) unstakeInfo2.get(0).get("unstakeBlockHeight");
        Assertions.assertEquals(unstakeExpected, stakingScoreClient.getUserUnstakeInfo(senderAddress).get(0).get("blockHeight"));
        assertEquals(userDelegations, userExpectedDelegations);

    }

    @Test
    @Order(16)
    void unstakeFull() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");


        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger userBalance = sicxScoreClient.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScoreClient.balanceOf(testerAddress);
        Address delegatedAddress = stakingScoreClient.getTopPreps().get(7);
        BigInteger previousUnstakingAmount = stakingScoreClient.getUnstakingAmount();

        BigInteger toTransfer = new BigInteger("4000000000000000000");
        sicxScoreClient.transfer(stakingAddress, toTransfer, data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingScoreClient.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingScoreClient.getPrepList();
        List<Address> topPreps = stakingScoreClient.getTopPreps();

        BigInteger currentTotalStake = stakingScoreClient.getTotalStake();
        BigInteger topPrepSize = BigInteger.valueOf(topPreps.size());
        BigInteger remainingUserBalance = currentTotalStake.subtract(testerBalance);
        BigInteger toDistributeEvenly = remainingUserBalance.divide(topPrepSize);
        BigInteger toDistributeEvenlyNetwork = currentTotalStake.subtract(testerBalance);

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("0"));
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenly));
                expectedNetworkDelegations.put(prep.toString(), testerBalance.add(toDistributeEvenlyNetwork.divide(topPrepSize)));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), toDistributeEvenlyNetwork.divide(topPrepSize));
                expectedPrepDelegations.put(prep.toString(), toDistributeEvenly);
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("0"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingScoreClient.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(toTransfer),
                stakingScoreClient.getTotalStake());
        assertEquals(previousUnstakingAmount.add(toTransfer), stakingScoreClient.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(toTransfer),
                sicxScoreClient.totalSupply());
        assertEquals(userBalance.subtract(toTransfer),
                sicxScoreClient.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = stakingScoreClient.getUserUnstakeInfo(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(toTransfer, new BigInteger(hexValue, 16));
        List<List<Object>> unstakeInfo = stakingScoreClient.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4).toString());
        assertEquals(toTransfer, new BigInteger(hexValue, 16));
        assertEquals(previousTotalStake.subtract(toTransfer),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(17)
    void stakeAfterUnstake() {
        BigInteger toStake = new BigInteger("1").multiply(ONE_EXA);
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake,
                null, null);
        Assertions.assertEquals(new BigInteger("7").multiply(ONE_EXA), stakingScoreClient.getUnstakingAmount());

        List<Map<String, Object>> userUnstakeInfo = stakingScoreClient.getUserUnstakeInfo(senderAddress);
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        Assertions.assertEquals(new BigInteger("3").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        Assertions.assertEquals(new BigInteger("1").multiply(ONE_EXA), stakingScoreClient.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("1").multiply(ONE_EXA), stakingScoreClient.claimableICX(senderAddress));
        List<List<Object>> unstakeInfo = stakingScoreClient.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(2));
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(4));
        List<List<Object>> unstakeList = stakingScoreClient.getUnstakeInfo();
        String hexVal = (String) unstakeList.get(1).get(1);
        hexVal = hexVal.replace("0x", "");
        Assertions.assertEquals(new BigInteger("4").multiply(ONE_EXA), new BigInteger(hexVal, 16));
        assertEquals(previousTotalStake.add(new BigInteger("1").multiply(ONE_EXA)), stakingScoreClient.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("1").multiply(ONE_EXA)), sicxScoreClient.totalSupply());
    }

    @Test
    @Order(18)
    void claimUnstakedICX() {
        BigInteger previousTotalStake = stakingScoreClient.getTotalStake();
        BigInteger previousTotalSupply = sicxScoreClient.totalSupply();
        BigInteger toStake = new BigInteger("2").multiply(ONE_EXA);
        ((StakingScoreClient) stakingScoreClient).stakeICX(toStake,
                null, null);
        assertEquals(previousTotalStake.add(toStake), stakingScoreClient.getTotalStake());
        assertEquals(previousTotalSupply.add(toStake), sicxScoreClient.totalSupply());

        Assertions.assertEquals(new BigInteger("3").multiply(ONE_EXA), stakingScoreClient.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("3").multiply(ONE_EXA), stakingScoreClient.claimableICX(senderAddress));
        Assertions.assertEquals(new BigInteger("0").multiply(ONE_EXA), stakingScoreClient.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        Assertions.assertEquals(new BigInteger("5").multiply(ONE_EXA), stakingScoreClient.getUnstakingAmount());

        ((StakingScoreClient) stakingScoreClient).claimUnstakedICX(senderAddress);
        ((StakingScoreClient) stakingScoreClient).claimUnstakedICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
    }


}