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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.core.reserve.ReserveImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;


import java.math.BigInteger;

import static network.balanced.score.core.reserve.Constants.ONE_EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@TestInstance(PER_CLASS)
class ReserveImplTest extends TestBase {

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    private BigInteger rewards = BigInteger.TEN;
    private Score reserve;
    private ReserveImpl reserveSpy;
    protected static final Account staking = Account.newScoreAccount(1);
    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);



    @BeforeEach
    void setUp() throws Exception {
        setupReserveScore();
    }

    private void setupReserveScore() throws Exception {
        reserve = sm.deploy(owner, ReserveImpl.class);
        reserveSpy = (ReserveImpl) spy(reserve.getInstance());
        reserve.setInstance(reserveSpy);

        // Configure Reserve contract
        reserve.invoke(owner, "setStakingAddress", staking.getAddress());
        reserve.invoke(owner, "setOwnerPercentage", rewards.multiply(ONE_EXA));
    }

    @Test
    void name() {
        assertEquals("Staked ICX Manager Reserve", reserve.call("name"));
    }

    @Test
    void getStakingAddress(){
        reserve.invoke(owner, "setStakingAddress", staking.getAddress());
        assertEquals(staking.getAddress(), reserve.call("getStakingAddress"));
    }

    @Test
    void getOwnerRewards(){
        reserve.invoke(owner, "setOwnerPercentage", rewards);
        assertEquals(rewards, reserve.call("getOwnerPercentage"));
    }

    @Test
    void transferOwnerRewards(){
        contextMock.when(() -> Context.getBalance(any(Address.class))).thenReturn(BigInteger.valueOf(200L).multiply(ONE_EXA));
        contextMock.when(()->Context.transfer(any(Address.class), any(BigInteger.class))).then(invocationOnMock -> null);
        reserve.invoke(owner, "transferOwnerRewards");
        verify(reserveSpy).FundTransfer(owner.getAddress(), BigInteger.valueOf(20).multiply(ONE_EXA), "ICX sent to owner");
        contextMock.verify(() -> Context.transfer(owner.getAddress(), BigInteger.valueOf(20).multiply(ONE_EXA)));
    }
}
