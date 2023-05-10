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

package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import score.Address;
import score.annotation.Keep;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface SystemInterface {
    public class Delegation{
        @Keep
        public Address address;
        @Keep
        public BigInteger value;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Delegation that = (Delegation) o;
            return address.equals(that.address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }
    Map<String, Object> getIISSInfo();

    Map<String, Object> queryIScore(Address address);

    Map<String, Object> getStake(Address address);

    Map<String, Object> getDelegation(Address address);

    Map<String, Object> getPReps(BigInteger startRanking, BigInteger endRanking);

    Map<String, Object> getPRep(Address address);

    @Payable
    void registerPRep(String name, String email, String country, String city, String website, String details, String p2pEndpoint);
}
