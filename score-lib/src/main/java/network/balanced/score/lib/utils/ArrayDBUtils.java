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

package network.balanced.score.lib.utils;

import score.ArrayDB;

public class ArrayDBUtils {
    public static <T> boolean arrayDBContains(ArrayDB<T> arrayDB, T ele) {
        for (int i = 0; i < arrayDB.size(); i++) {
            if (arrayDB.get(i).equals(ele)) {
                return true;
            }
        }
        return false;
    }

    public static <T> void removeFromArrayDB(T ele, ArrayDB<T> arrayDB) {
        for (int i = 0; i < arrayDB.size(); i++) {
            if (arrayDB.get(i).equals(ele)) {
                arrayDB.set(i, arrayDB.pop());
            }
        }
    }
}
