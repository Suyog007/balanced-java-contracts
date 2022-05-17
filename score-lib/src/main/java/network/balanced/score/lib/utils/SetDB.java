package network.balanced.score.lib.utils;

import score.Context;


public class SetDB<T> extends BagDB<T> {

    //private static final String NAME = "_SETDB";

    public SetDB(String key, Class<T> valueType, Boolean order) {
        super(key + "_SETDB", valueType, order);
    }

    public void add(T item) {
        if (!super.contains(item)) {
            super.add(item);
        }
    }

    public void remove(T item) {
        if (!super.contains(item)) {
            Context.revert("Item not found " + item);
        }
        super.remove(item);
    }

}


