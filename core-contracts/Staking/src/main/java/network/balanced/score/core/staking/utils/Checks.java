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

package network.balanced.score.core.staking.utils;

import network.balanced.score.core.staking.StakingImpl;
import score.Address;
import score.Context;

public class Checks {

    public static Address defaultAddress = new Address(new byte[Address.LENGTH]);

    public static void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), "SenderNotScoreOwner: Sender=" + caller + "Owner=" + owner);
    }

    public static void stakingOn() {
        if (!StakingImpl.stakingOn.get()) {
            Context.revert(Constant.TAG + ": ICX Staking SCORE is not active.");
        }
    }
}