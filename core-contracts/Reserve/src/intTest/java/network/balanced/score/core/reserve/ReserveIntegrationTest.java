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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.model.TransactionResult;

import static  network.balanced.score.lib.utils.Constants.*;
import static  network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

class ReserveIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;

    protected static BigInteger voteDefinitionFee = BigInteger.TEN.pow(10);
    @BeforeAll
    public static void contractSetup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        
        owner.stability.whitelistTokens(balanced.sicx._address(), BigInteger.TEN.pow(10));

        owner.baln.toggleEnableSnapshot();

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });

        owner.governance.setRebalancingThreshold(BigInteger.TEN.pow(17));
        owner.governance.setVoteDuration(BigInteger.TWO);
        owner.governance.setVoteDefinitionFee(voteDefinitionFee);
        owner.governance.setBalnVoteDefinitionCriterion(BigInteger.ZERO);
        owner.governance.setQuorum(BigInteger.ONE);

        setupBaseLoans();
    }

    private static void setupBaseLoans() throws Exception {
        BalancedClient loantakerICX1 = balanced.newClient();
        BalancedClient loantakerICX2 = balanced.newClient();
        BalancedClient loantakerICX3 = balanced.newClient();
        BalancedClient loantakerICX4 = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);

        loantakerICX1.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX2.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX3.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        loantakerICX4.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
    }

    @Test
    void liquidate_throughGovernanceVote_useReserve() throws Exception {
        // Arrange
        balanced.increaseDay(1);
        claimAllRewards();
        BalancedClient voter = balanced.newClient();
        depositToStabilityContract(voter, voteDefinitionFee.multiply(BigInteger.TWO));

        BigInteger initalLockingRatio = hexObjectToBigInteger(owner.loans.getParameters().get("locking ratio"));
        BigInteger lockingRatio = BigInteger.valueOf(8500);

        BalancedClient loanTaker = balanced.newClient();
        BalancedClient liquidator = balanced.newClient();
        
        setLockingRatio(voter, lockingRatio, "Liquidation setup with reserve");

        BigInteger collateral = BigInteger.TEN.pow(23);
        owner.staking.stakeICX(collateral, null, null);
        owner.sicx.transfer(balanced.reserve._address(), owner.sicx.balanceOf(owner.getAddress()), null);
        BigInteger reserveSICXBalance = reader.reserve.getBalances().get("sICX");
        assertTrue(reserveSICXBalance.compareTo(BigInteger.ZERO) > 0);

        BigInteger collateralValue = collateral.multiply(owner.sicx.lastPriceInLoop()).divide(EXA);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger maxDebt = POINTS.multiply(collateralValue).divide(lockingRatio);
        BigInteger maxFee = maxDebt.multiply(feePercent).divide(POINTS);
        BigInteger loan  = (maxDebt.subtract(maxFee)).multiply(EXA).divide(owner.bnUSD.lastPriceInLoop());
        BigInteger fee = loan.multiply(feePercent).divide(POINTS);

        loanTaker.loans.depositAndBorrow(collateral, "bnUSD", loan,  null, null);

        setLockingRatio(voter, initalLockingRatio, "restore Lockign ratio 32");

        // Act
        BigInteger balancePreLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.liquidate(loanTaker.getAddress());
        BigInteger balancePostLiquidation = liquidator.sicx.balanceOf(liquidator.getAddress());
        assertTrue(balancePreLiquidation.compareTo(balancePostLiquidation) < 0);

        depositToStabilityContract(liquidator, loan.multiply(BigInteger.TWO));

        BigInteger bnUSDBalancePreRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePreRetire = liquidator.sicx.balanceOf(liquidator.getAddress());
        liquidator.loans.retireBadDebt("bnUSD", loan.add(fee));
        BigInteger bnUSDBalancePostRetire = liquidator.bnUSD.balanceOf(liquidator.getAddress());
        BigInteger sICXBalancePostRetire = liquidator.sicx.balanceOf(liquidator.getAddress());

        // Assert
        assertTrue(bnUSDBalancePreRetire.compareTo(bnUSDBalancePostRetire) > 0);
        assertTrue(sICXBalancePreRetire.compareTo(sICXBalancePostRetire) < 0);

        Map<String, BigInteger> LiquidatedUserBaS = reader.loans.getBalanceAndSupply("Loans", loanTaker.getAddress());
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("sICX"));
        assertEquals(BigInteger.ZERO, loanTaker.getLoansAssetPosition("bnUSD"));
        assertEquals(BigInteger.ZERO, LiquidatedUserBaS.get("_balance"));

        BigInteger reserveSICXBalanceAfterRedeem = reader.reserve.getBalances().get("sICX");
        System.out.println(reserveSICXBalance);
        System.out.println(reserveSICXBalanceAfterRedeem);
        assertTrue(reserveSICXBalanceAfterRedeem.compareTo(reserveSICXBalance) < 0);
    }

    protected void depositToStabilityContract(BalancedClient client, BigInteger icxAmount) {
        client.staking.stakeICX(icxAmount, null, null);
        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(balanced.stability._address(), sicxDeposit, null);
    }

    protected void claimAllRewards() {
        for (BalancedClient client : balanced.balancedClients.values()) {
            if(client.rewards.getBalnHolding(client.getAddress()).compareTo(EXA) < 0) {
                continue;
            }

            client.rewards.claimRewards();
            BigInteger balance = client.baln.balanceOf(client.getAddress());
            if (balance.compareTo(EXA) > 0) {
                client.baln.stake(balance);
            }
        }
    }

    protected void setLockingRatio(BalancedClient voter, BigInteger ratio, String name) throws Exception {
        JsonObject setLockingRatioParameters = new JsonObject()
        .add("_value", ratio.intValue());
        
        JsonArray setLockingRatioCall = new JsonArray()
            .add("setLockingRatio")
            .add(setLockingRatioParameters);

        JsonArray actions = new JsonArray()
            .add(setLockingRatioCall);
        executeVoteActions(balanced, voter, name, actions);
    }
}
