/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal.aop;

import static java.util.Arrays.binarySearch;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Immutable space-efficient trie that provides an index for a sorted list of up to 65536 strings.
 * It assumes only those strings will be queried and therefore may produce false-positive results
 * for strings not in the list.
 *
 * <p>Each node of the tree is represented as a series of {@code char}s using this layout:
 *
 * <pre>
 * +---------------------------------+
 * | number of branches              |
 * +---------------------------------+---------------------------------+----
 * | char for branch 0               | char for branch 1               | ...
 * +---------------------------------+---------------------------------+----
 * | key-delta/leaf/bud for branch 0 | key-delta/leaf/bud for branch 1 | ...
 * +---------------------------------+---------------------------------+----
 * | offset to jump to branch 1      | offset to jump to branch 2      | ...
 * +---------------------------------+---------------------------------+----
 * </pre>
 *
 * Each node is immediately followed by its child nodes according to branch order.
 *
 * <p>The key-delta is used to skip over a section of the input key when we know it should always
 * match given the recently matched char (assumes only strings from the original list are queried).
 *
 * <p>Leaves mark a definite end of the match, while buds mark a potential end which could continue
 * down the trie if there are more characters to match. The key-delta for buds is implicitly 1.
 *
 * <p>The jump section is omitted when all the branches from a node are leaves.
 *
 * <p>Simple example: getValue, setValue
 *
 * <pre>
 * +---+---+---+--------+--------+
 * | 2 | g | s | 0x8000 | 0x8001 |
 * +---+---+---+--------+--------+
 * </pre>
 *
 * In this case the first character is enough to determine the index result.
 *
 * <p>Example of a trie with a 'bud': getName, getNameAndValue
 *
 * <pre>
 * +---+---+---+---+---+--------+---+---+--------+
 * | 1 | g | 6 | 1 | e | 0x4000 | 1 | A | 0x8001 |
 * +---+---+---+---+---+--------+---+---+--------+
 * </pre>
 *
 * After matching 'g' we skip to the end of 'getName' before checking if there are any more
 * characters to match.
 *
 * <p>More complex example: getName, getValue, getVersion
 *
 * <pre>
 * +---+---+---+---+---+---+--------+---+---+---+---+---+--------+--------+
 * | 1 | g | 3 | 2 | N | V | 0x8000 | 1 | 0 | 2 | a | e | 0x8001 | 0x8002 |
 * +---+---+---+---+---+---+--------+---+---+---+---+---+--------+--------+
 * </pre>
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class ImmutableStringTrie implements ToIntFunction<String> {

  /** Maximum number of rows that can be indexed by a single trie. */
  private static final int MAX_ROWS_PER_TRIE = 0x4000;

  // Row limits marking the boundaries between tries

  private static final int TRIE_1_LIMIT = 1 * MAX_ROWS_PER_TRIE;
  private static final int TRIE_2_LIMIT = 2 * MAX_ROWS_PER_TRIE;
  private static final int TRIE_3_LIMIT = 3 * MAX_ROWS_PER_TRIE;
  private static final int TRIE_4_LIMIT = 4 * MAX_ROWS_PER_TRIE;

  // Individual tries that together can index 65536 entries.

  private final char[] trie1;
  private final char[] trie2;
  private final char[] trie3;
  private final char[] trie4;

  // Keys from the start of each overflow trie.

  private final String key2;
  private final String key3;
  private final String key4;

  /** Marks a leaf in the trie, where the rest of the bits are the index to be returned. */
  private static final char LEAF_MARKER = 0x8000;

  /** Marks a 'bud' in the tree; the same as a leaf except the trie continues beneath it. */
  private static final char BUD_MARKER = 0x4000;

  /**
   * Returns the index assigned in the trie to the given string.
   *
   * <p>Note: a return value of {@code -1} means the string is definitely not in the trie, but a
   * non-negative index may be returned for strings that closely match those in the trie. This is
   * acceptable because we will only call this method with strings that we know exist in the trie.
   */
  @Override
  public int applyAsInt(String key) {
    int keyLength = key.length();
    char[] data;
    int offset;

    // use overflow keys to pick the right trie
    if (key2 == null || key.compareTo(key2) < 0) {
      data = trie1;
      offset = 0;
    } else if (key3 == null || key.compareTo(key3) < 0) {
      data = trie2;
      offset = TRIE_1_LIMIT;
    } else if (key4 == null || key.compareTo(key4) < 0) {
      data = trie3;
      offset = TRIE_2_LIMIT;
    } else {
      data = trie4;
      offset = TRIE_3_LIMIT;
    }

    int keyIndex = 0;
    int dataIndex = 0;

    while (keyIndex < keyLength) {
      // trie is ordered, so we can use binary search to pick the right branch
      int branchCount = data[dataIndex++];
      int branchIndex =
          binarySearch(data, dataIndex, dataIndex + branchCount, key.charAt(keyIndex));

      if (branchIndex < 0) {
        break; // definitely no match
      }

      int resultIndex = branchIndex + branchCount;
      char result = data[resultIndex];
      if ((result & LEAF_MARKER) != 0) {
        return offset + (result & ~LEAF_MARKER);
      }

      // 'buds' are just like leaves unless the key still has characters left
      if ((result & BUD_MARKER) != 0) {
        if (keyIndex == keyLength - 1) {
          return offset + (result & ~BUD_MARKER);
        }
        result = 1; // more characters to match, continue search with next character
      }

      // move the key to the next potential decision point
      keyIndex += result;

      // move the data to the appropriate branch...
      if (branchIndex > dataIndex) {
        int jumpIndex = resultIndex + branchCount - 1;
        dataIndex += data[jumpIndex];
      }

      // ...always include moving past the current node
      dataIndex += (branchCount * 3) - 1;
    }

    return -1;
  }

  /**
   * Builds an immutable trie that indexes the given table of strings.
   *
   * <p>The table of strings must be sorted in lexical order.
   */
  public static ImmutableStringTrie build(List<String> table) {
    StringBuilder buf = new StringBuilder();
    int tableSize = table.size();

    if (tableSize <= TRIE_1_LIMIT) {
      buildTrie(buf, table, 0, 0, tableSize);
      return new ImmutableStringTrie(buf.toString());
    }

    buildTrie(buf, table, 0, 0, TRIE_1_LIMIT);
    String trie1 = buf.toString();
    buf.setLength(0);

    String key2 = table.get(TRIE_1_LIMIT);
    if (tableSize <= TRIE_2_LIMIT) {
      buildTrie(buf, table, 0, TRIE_1_LIMIT, tableSize);
      return new ImmutableStringTrie(trie1, key2, buf.toString());
    }

    buildTrie(buf, table, 0, TRIE_1_LIMIT, TRIE_2_LIMIT);
    String trie2 = buf.toString();
    buf.setLength(0);

    String key3 = table.get(TRIE_2_LIMIT);
    if (tableSize <= TRIE_3_LIMIT) {
      buildTrie(buf, table, 0, TRIE_2_LIMIT, tableSize);
      return new ImmutableStringTrie(trie1, key2, trie2, key3, buf.toString());
    }

    buildTrie(buf, table, 0, TRIE_2_LIMIT, TRIE_3_LIMIT);
    String trie3 = buf.toString();
    buf.setLength(0);

    String key4 = table.get(TRIE_3_LIMIT);
    if (tableSize <= TRIE_4_LIMIT) {
      buildTrie(buf, table, 0, TRIE_3_LIMIT, tableSize);
      return new ImmutableStringTrie(trie1, key2, trie2, key3, trie3, key4, buf.toString());
    }

    throw new IllegalArgumentException("Input list is too large: " + tableSize);
  }

  private ImmutableStringTrie(String trie1) {
    this.trie1 = trie1.toCharArray();
    this.key2 = null;
    this.trie2 = null;
    this.key3 = null;
    this.trie3 = null;
    this.key4 = null;
    this.trie4 = null;
  }

  private ImmutableStringTrie(String trie1, String key2, String trie2) {
    this.trie1 = trie1.toCharArray();
    this.key2 = key2;
    this.trie2 = trie2.toCharArray();
    this.key3 = null;
    this.trie3 = null;
    this.key4 = null;
    this.trie4 = null;
  }

  private ImmutableStringTrie(String trie1, String key2, String trie2, String key3, String trie3) {
    this.trie1 = trie1.toCharArray();
    this.key2 = key2;
    this.trie2 = trie2.toCharArray();
    this.key3 = key3;
    this.trie3 = trie3.toCharArray();
    this.key4 = null;
    this.trie4 = null;
  }

  private ImmutableStringTrie(
      String trie1,
      String key2,
      String trie2,
      String key3,
      String trie3,
      String key4,
      String trie4) {
    this.trie1 = trie1.toCharArray();
    this.key2 = key2;
    this.trie2 = trie2.toCharArray();
    this.key3 = key3;
    this.trie3 = trie3.toCharArray();
    this.key4 = key4;
    this.trie4 = trie4.toCharArray();
  }

  /** Recursively builds a trie for a slice of rows at a particular column. */
  private static void buildTrie(
      StringBuilder buf, List<String> table, int column, int row, int rowLimit) {

    int trieStart = buf.length();

    int lastRow = row;
    int branchCount = 0;
    int nextJump = 0;

    boolean allLeaves = true;

    while (lastRow < rowLimit) {
      String cells = table.get(lastRow);
      int columnLimit = cells.length();

      char pivot = cells.charAt(column);

      // find the row that marks the start of the next branch, and the end of this one
      int nextRow = nextPivotRow(table, pivot, column, lastRow, rowLimit);

      // find the column along this branch that marks the next decision point/pivot
      int nextColumn = nextPivotColumn(table, column, lastRow, nextRow);

      // at the end of our row, check in case there are further rows that we'd normally
      // handle with a bud, but we can't because our branch spans more than one column
      if (nextColumn == columnLimit && nextColumn - column > 1 && nextRow - lastRow > 1) {
        // set the next column to just before the end of our row so we can insert a bud
        nextColumn--;
      }

      int branchIndex = trieStart + branchCount;
      buf.insert(branchIndex, pivot);

      int resultIndex = branchIndex + 1 + branchCount;

      // sub trie will start after the result (to be inserted)
      int subTrieStart = buf.length() + 1;

      if (nextColumn < columnLimit) {
        buf.insert(resultIndex, (char) (nextColumn - column));
        buildTrie(buf, table, nextColumn, lastRow, nextRow);
        allLeaves = false;
      } else {
        buildTrie(buf, table, nextColumn, lastRow + 1, nextRow);
        boolean isLeaf = subTrieStart > buf.length(); // only true if nothing was added
        char marker = isLeaf ? LEAF_MARKER : BUD_MARKER;
        buf.insert(resultIndex, (char) (lastRow & (MAX_ROWS_PER_TRIE - 1) | marker));
        allLeaves = allLeaves && isLeaf;
      }

      if (nextRow < rowLimit) {
        int jumpIndex = resultIndex + 1 + branchCount;
        nextJump += buf.length() - subTrieStart;
        buf.insert(jumpIndex, (char) nextJump);
      }

      lastRow = nextRow;
      branchCount++;
    }

    if (branchCount > 0) {
      buf.insert(trieStart, (char) branchCount);
      if (allLeaves) {
        // no need for jumps when every branch is a leaf
        int jumpStart = trieStart + 1 + (branchCount * 2);
        buf.delete(jumpStart, jumpStart + branchCount);
      }
    }
  }

  /**
   * Finds the next row that has a different character in the selected column to the given one, or
   * is too short to include the column. This determines the span of rows that fall under the given
   * character in the trie.
   *
   * <p>Returns the row just after the end of the range if all rows have the same character.
   */
  private static int nextPivotRow(
      List<String> table, char pivot, int column, int row, int rowLimit) {

    for (int r = row + 1; r < rowLimit; r++) {
      String cells = table.get(r);
      if (cells.length() <= column || cells.charAt(column) != pivot) {
        return r;
      }
    }

    return rowLimit;
  }

  /**
   * Finds the next column in the current row whose character differs in at least one other row.
   * This helps identify the longest common prefix from the current pivot point to the next one.
   *
   * <p>Returns the column just after the end of the current row if all rows are identical.
   */
  private static int nextPivotColumn(List<String> table, int column, int row, int rowLimit) {

    String cells = table.get(row);
    int columnLimit = cells.length();

    for (int c = column + 1; c < columnLimit; c++) {
      if (nextPivotRow(table, cells.charAt(c), c, row, rowLimit) < rowLimit) {
        return c;
      }
    }

    return columnLimit;
  }
}
