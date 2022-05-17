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

package network.balanced.score.lib.test.integration;

import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.BalancedAddresses;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;

public class Balanced {
    public KeyWallet owner;
    public BalancedClient ownerClient;
    public DefaultScoreClient governance;
    public DefaultScoreClient baln;
    public DefaultScoreClient bwt;
    public DefaultScoreClient dex;
    public DefaultScoreClient feehandler;
    public DefaultScoreClient loans;
    public DefaultScoreClient rebalancing;
    public DefaultScoreClient rewards;
    public DefaultScoreClient sicx;
    public DefaultScoreClient bnusd;
    public DefaultScoreClient daofund;
    public DefaultScoreClient dividends;
    public DefaultScoreClient oracle;
    public DefaultScoreClient reserve;
    public DefaultScoreClient router;
    public DefaultScoreClient staking;
    public DefaultScoreClient stakedLp;

    @ScoreClient
    Governance governanceScore;

    @ScoreClient
    Staking stakingScore;

    @ScoreClient
    BalancedDollar bnUSD;
    
    @ScoreClient
    DAOfund daofundScore;

    public Balanced() {
       
    }

    public void deployBalanced() throws Exception {
        owner = createWalletWithBalance(BigInteger.TEN.multiply(BigInteger.TEN.pow(24)));
        deployPrep();

        governance = deploy(owner, "Governance", null);
        governanceScore = new GovernanceScoreClient(governance);
        deployContracts();
        ownerClient = new BalancedClient(this, owner);
    
        setupAddresses();
        setupContracts();
        // delegate(adminWallet);
        ownerClient = new BalancedClient(this, owner);
        ownerClient.governance.createBnusdMarket(BigInteger.valueOf(210).multiply(BigInteger.TEN.pow(18)));
    }

    protected void deployPrep() {
        try {
            systemScore.registerPRep(BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), "test", "kokoa@example.com", "USA", "New York", "https://icon.kokoa.com", "https://icon.kokoa.com/json/details.json", "localhost:9082");
        } catch (Exception e) {
            //Already registerd
        }
    }

    protected void deployContracts() {
        baln = deploy(owner, "Baln", Map.of("_governance", governance._address()));
        bwt = deploy(owner, "Bwt", Map.of("_governance", governance._address()));
        dex = deploy(owner, "Dex", Map.of("_governance", governance._address()));
        feehandler = deploy(owner, "Feehandler", Map.of("_governance", governance._address()));
        loans = deploy(owner, "Loans", Map.of("_governance", governance._address()));
        rebalancing = deploy(owner, "Rebalancing", Map.of("_governance", governance._address()));
        rewards = deploy(owner, "Rewards", Map.of("_governance", governance._address()));
        staking = deploy(owner, "Staking", null);
        sicx = deploy(owner, "Sicx", Map.of("_admin", staking._address()));
        bnusd = deploy(owner, "BalancedDollar", Map.of("_governance", governance._address()));
        daofund = deploy(owner, "DAOfund", Map.of("_governance", governance._address()));
        dividends = deploy(owner, "Dividends", Map.of("_governance", governance._address()));
        oracle = deploy(owner, "Oracle",null);
        reserve = deploy(owner, "Reserve", Map.of("governance", governance._address()));
        router = deploy(owner, "Router", Map.of("_governance", governance._address()));
        stakedLp = deploy(owner, "StakedLP", Map.of("governance", governance._address()));
    }

    protected void setupAddresses() {
        BalancedAddresses balancedAddresses = new BalancedAddresses();
        balancedAddresses.loans = loans._address();
        balancedAddresses.dex = dex._address();
        balancedAddresses.staking = staking._address();
        balancedAddresses.rewards = rewards._address();
        balancedAddresses.reserve = reserve._address();
        balancedAddresses.dividends = dividends._address();
        balancedAddresses.daofund = daofund._address();
        balancedAddresses.oracle = oracle._address();
        balancedAddresses.sicx = sicx._address();
        balancedAddresses.bnUSD = bnusd._address();
        balancedAddresses.baln = baln._address();
        balancedAddresses.bwt = bwt._address();
        balancedAddresses.router = router._address();
        balancedAddresses.rebalancing = rebalancing._address();
        balancedAddresses.feehandler = feehandler._address();
        balancedAddresses.stakedLp = stakedLp._address();

        ownerClient.governance.setAddresses(balancedAddresses);
        ownerClient.governance.setAdmins();
        ownerClient.governance.setContractAddresses();

    }

    protected void setupContracts() {
        ownerClient.staking.setSicxAddress(sicx._address());

        ownerClient.bnUSD.setMinter(loans._address());
        ownerClient.governance.configureBalanced();
        ownerClient.governance.launchBalanced();
        ownerClient.staking.toggleStakingOn();

        ownerClient.daofund.addAddressToSetdb();
    }
}
