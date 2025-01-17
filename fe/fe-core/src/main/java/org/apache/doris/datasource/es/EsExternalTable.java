// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.datasource.es;

import org.apache.doris.catalog.Column;
import org.apache.doris.catalog.EsTable;
import org.apache.doris.datasource.ExternalTable;
import org.apache.doris.datasource.SchemaCacheValue;
import org.apache.doris.thrift.TEsTable;
import org.apache.doris.thrift.TTableDescriptor;
import org.apache.doris.thrift.TTableType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elasticsearch external table.
 */
public class EsExternalTable extends ExternalTable {

    private EsTable esTable;

    /**
     * Create elasticsearch external table.
     *
     * @param id Table id.
     * @param name Table name.
     * @param remoteName Remote table name.
     * @param catalog EsExternalDataSource.
     * @param db Database.
     */
    public EsExternalTable(long id, String name, String remoteName, EsExternalCatalog catalog, EsExternalDatabase db) {
        super(id, name, remoteName, catalog, db, TableType.ES_EXTERNAL_TABLE);
    }

    protected synchronized void makeSureInitialized() {
        super.makeSureInitialized();
        if (!objectCreated) {
            esTable = toEsTable();
            objectCreated = true;
        }
    }

    public EsTable getEsTable() {
        makeSureInitialized();
        return esTable;
    }

    @Override
    public TTableDescriptor toThrift() {
        List<Column> schema = getFullSchema();
        TEsTable tEsTable = new TEsTable();
        TTableDescriptor tTableDescriptor = new TTableDescriptor(getId(), TTableType.ES_TABLE, schema.size(), 0,
                getName(), "");
        tTableDescriptor.setEsTable(tEsTable);
        return tTableDescriptor;
    }

    @Override
    public Optional<SchemaCacheValue> initSchema() {
        EsRestClient restClient = ((EsExternalCatalog) catalog).getEsRestClient();
        Map<String, String> column2typeMap = new HashMap<>();
        List<Column> columns = EsUtil.genColumnsFromEs(restClient, name, null,
                ((EsExternalCatalog) catalog).enableMappingEsId(), column2typeMap);
        return Optional.of(new EsSchemaCacheValue(columns, column2typeMap));
    }

    public Map<String, String> getColumn2type() {
        Optional<SchemaCacheValue> schemaCacheValue = getSchemaCacheValue();
        return schemaCacheValue.map(value -> ((EsSchemaCacheValue) value).getColumn2typeMap()).orElse(null);
    }

    private EsTable toEsTable() {
        List<Column> schema = getFullSchema();
        Map<String, String> column2typeMap = getColumn2type();
        EsExternalCatalog esCatalog = (EsExternalCatalog) catalog;
        EsTable esTable = new EsTable(this.id, this.name, schema, TableType.ES_EXTERNAL_TABLE);
        esTable.setIndexName(name);
        esTable.setClient(esCatalog.getEsRestClient());
        esTable.setUserName(esCatalog.getUsername());
        esTable.setPasswd(esCatalog.getPassword());
        esTable.setEnableDocValueScan(esCatalog.enableDocValueScan());
        esTable.setEnableKeywordSniff(esCatalog.enableKeywordSniff());
        esTable.setNodesDiscovery(esCatalog.enableNodesDiscovery());
        esTable.setHttpSslEnabled(esCatalog.enableSsl());
        esTable.setLikePushDown(esCatalog.enableLikePushDown());
        esTable.setSeeds(esCatalog.getNodes());
        esTable.setHosts(String.join(",", esCatalog.getNodes()));
        esTable.syncTableMetaData();
        esTable.setIncludeHiddenIndex(esCatalog.enableIncludeHiddenIndex());
        esTable.setColumn2typeMap(column2typeMap);
        return esTable;
    }
}
