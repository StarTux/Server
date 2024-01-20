package com.cavetale.server.sql;

import com.winthier.sql.SQLDatabase;
import com.winthier.sql.SQLRow;
import org.junit.Test;

public final class SQLTest {
    @Test
    public void test() {
        for (Class<? extends SQLRow> tableClass : SQLStatic.getDatabaseClasses()) {
            System.out.println(SQLDatabase.testTableCreation(tableClass));
        }
    }
}
