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

abstract class LoansIntegrationTest implements ScoreIntegrationTest {
    protected static Balanced balanced;
    protected static BalancedClient owner;
    protected static BalancedClient reader;
    protected static BigInteger governanceDebt;

    @Test
    void testName() {
        assertEquals("Balanced Loans", reader.loans.name());
    }

    @Test
    @Order(1)
    void takeLoans() throws Exception {
        // Arrange
        BalancedClient loantakerICX = balanced.newClient();
        BalancedClient loantakerSICX = balanced.newClient();
        BalancedClient twoStepLoanTaker = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);

        // Act
        loantakerICX.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(collateral, "bnUSD", BigInteger.ZERO, null, null);
        twoStepLoanTaker.loans.depositAndBorrow(BigInteger.ZERO, "bnUSD", loanAmount, null, null);
        loantakerSICX.sICXDepositAndBorrow(collateral, loanAmount);

        // Assert
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);
        BigInteger totalDebt = debt.multiply(BigInteger.valueOf(3)).add(governanceDebt);

        Map<String, BigInteger> loantakerIcxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerICX.getAddress());
        Map<String, BigInteger> loantakerSicxBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());
        Map<String, BigInteger> twoStepLoanTakerBaS = reader.loans.getBalanceAndSupply("Loans", loantakerSICX.getAddress());

        assertEquals(totalDebt, loantakerIcxBaS.get("_totalSupply"));
        assertEquals(debt, loantakerIcxBaS.get("_balance"));
        assertEquals(debt, loantakerSicxBaS.get("_balance"));
        assertEquals(debt, twoStepLoanTakerBaS.get("_balance"));
    }

    @Test
    @Order(2)
    void repyDebt() throws Exception {
        // Arrange
        BalancedClient loanTakerFullRepay = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullRepay.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.sICXDepositAndBorrow(collateral, loanAmount);

        loanTakerPartialRepay.loans.returnAsset("bnUSD", debt.divide(BigInteger.TWO), true);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullRepay.getAddress(), fee, null);
        loanTakerFullRepay.loans.returnAsset("bnUSD", debt, true);

        BigInteger outstandingNewDebt = debt.divide(BigInteger.TWO);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(outstandingNewDebt);
        Map<String, BigInteger> loanTakerPartialRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerPartialRepay.getAddress());
        Map<String, BigInteger> loanTakerFullRepayBaS = reader.loans.getBalanceAndSupply("Loans", loanTakerFullRepay.getAddress());

        assertEquals(expectedTotalDebt, loanTakerPartialRepayBaS.get("_totalSupply"));
        assertEquals(BigInteger.ZERO, loanTakerFullRepayBaS.get("_balance"));
        assertEquals(debt.divide(BigInteger.TWO), loanTakerPartialRepayBaS.get("_balance"));
    }

    @Test
    @Order(3)
    void withdrawCollateral() throws Exception {
        // Arrange
        BalancedClient loanTakerFullWithdraw = balanced.newClient();
        BalancedClient loanTakerPartialWithdraw = balanced.newClient();
        BalancedClient zeroLoanWithdrawFull = balanced.newClient();
        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerFullWithdraw.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialWithdraw.sICXDepositAndBorrow(collateral, loanAmount);
        zeroLoanWithdrawFull.sICXDepositAndBorrow(collateral, BigInteger.ZERO);

        loanTakerPartialWithdraw.bnUSD.transfer(loanTakerFullWithdraw.getAddress(), fee, null);
        loanTakerFullWithdraw.loans.returnAsset("bnUSD", debt, true);
        BigInteger sICXCollateral = loanTakerFullWithdraw.getLoansAssetPosition("sICX");
        loanTakerFullWithdraw.loans.withdrawCollateral(sICXCollateral);

        assertThrows(UserRevertedException.class, () -> 
            loanTakerPartialWithdraw.loans.withdrawCollateral(sICXCollateral));
        BigInteger amountWithdrawn = BigInteger.TEN.pow(20);
        loanTakerPartialWithdraw.loans.withdrawCollateral(amountWithdrawn);

        zeroLoanWithdrawFull.loans.withdrawCollateral(sICXCollateral);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt);

        assertEquals(expectedTotalDebt, getTotalDebt());
        assertEquals(BigInteger.ZERO, loanTakerFullWithdraw.getLoansAssetPosition("sICX"));
        assertEquals(sICXCollateral.subtract(amountWithdrawn), loanTakerPartialWithdraw.getLoansAssetPosition("sICX"));
        assertEquals(BigInteger.ZERO, zeroLoanWithdrawFull.getLoansAssetPosition("sICX"));
    }

    @Test
    @Order(4)
    void reOpenPosition() throws Exception {
        // Arrange
        BalancedClient loanTakerCloseLoanOnly = balanced.newClient();
        BalancedClient loanTakerCloseLoanOnly2 = balanced.newClient();
        BalancedClient loanTakerFullClose = balanced.newClient();
        BalancedClient loanTakerPartialRepay = balanced.newClient();

        BigInteger collateral = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(22);
        BigInteger feePercent = hexObjectToBigInteger(owner.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger debt = loanAmount.add(fee);

        BigInteger initalTotalDebt = getTotalDebt();

        // Act
        loanTakerCloseLoanOnly.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerCloseLoanOnly2.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerFullClose.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.sICXDepositAndBorrow(collateral, loanAmount);

        loanTakerPartialRepay.bnUSD.transfer(loanTakerCloseLoanOnly.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerCloseLoanOnly2.getAddress(), fee, null);
        loanTakerPartialRepay.bnUSD.transfer(loanTakerFullClose.getAddress(), fee, null);

        loanTakerCloseLoanOnly.loans.returnAsset("bnUSD", debt, true);
        loanTakerCloseLoanOnly2.loans.returnAsset("bnUSD", debt, true);
        loanTakerFullClose.loans.returnAsset("bnUSD", debt, true);
        BigInteger amountRepaid = BigInteger.TEN.pow(21);
        loanTakerPartialRepay.loans.returnAsset("bnUSD", amountRepaid, true);

        loanTakerFullClose.loans.withdrawCollateral(loanTakerFullClose.getLoansAssetPosition("sICX"));

        loanTakerCloseLoanOnly.sICXDepositAndBorrow(BigInteger.ZERO, loanAmount);
        loanTakerCloseLoanOnly2.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerFullClose.sICXDepositAndBorrow(collateral, loanAmount);
        loanTakerPartialRepay.sICXDepositAndBorrow(BigInteger.ZERO, amountRepaid);

        // Assert
        BigInteger expectedTotalDebt = initalTotalDebt.add(debt.multiply(BigInteger.valueOf(4))).add(amountRepaid.multiply(feePercent).divide(POINTS));
        assertEquals(expectedTotalDebt, getTotalDebt());
    }


    private BigInteger getTotalDebt() {
        return reader.loans.getBalanceAndSupply("Loans", reader.getAddress()).get("_totalSupply");
    }
}
