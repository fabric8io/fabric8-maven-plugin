/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AsciiDoc utils for parsing specific elements coded in AsciiDoc format.
 */
public class AsciiDocParser {

    private static final String END_TABLE = "|===";

    /**
     *
     * This method serializes the content of an AsciiDoc table used to set the mapping between kinds and filenames.
     * These tables are of form:
     *
     * <pre>
     * [cols=2*,options="header"]
     * |===
     * |Kind
     * |File type
     *
     * a|ConfigMap
     * a|`cm`
     *
     * |CronJob
     * |cronjob
     * |===
     * </pre>
     *
     * Basically the table contains an optional attributes section (@code{[]}), then the headers of the columns.
     * Between line breaks, both columns representing the kind (first column) and filename (second column).
     * Notice that one-line columns definition @code{|Cell in column 1, row 1|Cell in column 1, row 1} is not supported.
     *
     * This method returns an @code{IllegalArgumentException} if does not contain two columns.
     *
     * @param table definition in AsciiDoc format.
     * @return A serialization of all columns, being pair elements the first column and the odd elements the second column. In previous example @code{"cm","ConfigMap","cronjob","CronJob"}
     */
    public Map<String, List<String>> serializeKindFilenameTable(final InputStream table) {

        final Map<String, List<String>> serializedContent = new HashMap<>();

        try (final BufferedReader tableContent = new BufferedReader(new InputStreamReader(table))) {

            skipUntilColumns(tableContent);

            boolean endTable = false;

            while (!endTable) {
                final List<String> readRow = readRow(tableContent);
                final String separator = readEmptyLineOrEndTable(tableContent);

                String kind = readRow.get(0);
                serializedContent.put(kind, readRow.subList(1, readRow.size()));

                if (END_TABLE.equals(separator)) {
                    endTable = true;
                }

            }

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return serializedContent;

    }

    private List<String> readRow(final BufferedReader tableContent) throws IOException {
        final List<String> content = new ArrayList<>();

        final String firstColumn = readColumn(tableContent);
        final String secondColumn = readColumn(tableContent);

        content.add(firstColumn);
        final String[] filenameTypes = secondColumn.split(",");

        for (String filenameType : filenameTypes) {
            content.add(filenameType.trim());
        }

        return content;
    }

    private String readColumn(final BufferedReader tableContent) throws IOException {
        final String column = tableContent.readLine();

        if(column == null || column.isEmpty()) {
            throw new IllegalArgumentException("Trying to read a column but white line or EOF was found.");
        }

        int separator;
        if ((separator = column.indexOf("|")) < 0) {
            throw new IllegalArgumentException(String.format("Expected the initial of a column with (|) but %s found.", column));
        }

        return column.trim().substring(separator + 1)
            .replaceAll("[`_*]", "")
            .trim();

    }

    /**
     * Reads empty line or throw an exception if a none empty line was found.
     */
    private String readEmptyLineOrEndTable(final BufferedReader tableContent) throws IOException {
        final String column = tableContent.readLine();

        if (column != null && column.startsWith(END_TABLE)) {
            return END_TABLE;
        }

        if(column == null || !column.isEmpty()) {
            throw new IllegalArgumentException(String.format("Trying to read an empty line for end of row, but content %s was found or EOF", column));
        }

        return "";
    }

    /**
     * Moves buffer until it finds the first content column (skipping headers).
     * @param tableContent
     */
    private void skipUntilColumns(final BufferedReader tableContent) throws IOException {
        String line;
        while ((line = tableContent.readLine()) != null) {
            if(line.trim().isEmpty()){
                break;
            }
        }
    }

}
