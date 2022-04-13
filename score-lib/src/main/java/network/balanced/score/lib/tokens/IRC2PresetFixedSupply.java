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

package network.balanced.score.lib.tokens;

import network.balanced.score.lib.interfaces.base.IRC2;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Math.pow;

public class IRC2PresetFixedSupply implements IRC2 {
    final private static String NAME = "name";
    final private static String SYMBOL = "symbol";
    final private static String DECIMALS = "decimals";
    final private static String TOTAL_SUPPLY = "total_supply";
    final private static String BALANCES = "balances";

    private final VarDB<String> name = Context.newVarDB(NAME, String.class);
    private final VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    private final VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    private final VarDB<BigInteger> totalSupply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);
    protected DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);

    /**
     * @param _tokenName     The name of the token.
     * @param _symbolName    The symbol of the token.
     * @param _initialSupply The total number of tokens to initialize with. It is set to total supply in the
     *                       beginning, 0 by default.
     * @param _decimals      The number of decimals. Set to 18 by default.
     */
    public IRC2PresetFixedSupply(String _tokenName, String _symbolName, @Optional BigInteger _initialSupply,
                                 @Optional BigInteger _decimals) {
        if (this.name.get() == null) {
            BigInteger initialSupply = (_initialSupply == null) ? BigInteger.ZERO : _initialSupply;
            BigInteger decimals = (_decimals == null) ? BigInteger.valueOf(18L) : _decimals;

            Context.require(decimals.compareTo(BigInteger.ZERO) >= 0, "Decimals cannot be less than zero");
            Context.require(initialSupply.compareTo(BigInteger.ZERO) > 0, "Initial Supply cannot be less than or " +
                    "equal to than zero");

            // set the total supply to the context variable
            BigInteger totalSupply = initialSupply.multiply(pow(BigInteger.TEN, decimals.intValue()));
            // set other values
            final Address caller = Context.getCaller();

            this.name.set(ensureNotEmpty(_tokenName));
            this.symbol.set(ensureNotEmpty(_symbolName));
            this.decimals.set(decimals);
            this.totalSupply.set(totalSupply);
            balances.set(caller, totalSupply);
        }
    }

    private String ensureNotEmpty(String str) {
        Context.require(str != null && !str.trim().isEmpty(), "str is null or empty");
        assert str != null;
        return str.trim();
    }

    /**
     * @return Name of the token
     */
    @External(readonly = true)
    public String name() {
        return name.get();
    }

    /**
     * @return Symbol of the token
     */
    @External(readonly = true)
    public String symbol() {
        return symbol.get();
    }

    /**
     * @return Number of decimals
     */
    @External(readonly = true)
    public BigInteger decimals() {
        return decimals.get();
    }

    /**
     * @return total number of tokens in existence.
     */
    @External(readonly = true)
    public BigInteger totalSupply() {
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    /**
     * @param _owner: The account whose balance is to be checked.
     * @return Amount of tokens owned by the `account` with the given address.
     */
    @External(readonly = true)
    public BigInteger balanceOf(Address _owner) {
        return balances.getOrDefault(_owner, BigInteger.ZERO);
    }

    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        transfer(Context.getCaller(), _to, _value, _data);
    }

    /**
     *
     * @param _to The account to which the token is to be transferred
     * @param _value The no. of tokens to be transferred
     * @param _data Any information or message
     */
    protected void transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0, this.name.get() + ": _value needs to be positive");
        Context.require(balanceOf(_from).compareTo(_value) >= 0, this.name.get() + ": Insufficient balance");

        this.balances.set(_from, balanceOf(_from).subtract(_value));
        this.balances.set(_to, balanceOf(_to).add(_value));

        byte[] dataBytes = (_data == null) ? new byte[0] : _data;
        Transfer(_from, _to, _value, dataBytes);

        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", _from, _value, dataBytes);
        }
    }

    @EventLog(indexed = 3)
    public void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
    }
}
