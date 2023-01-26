package com.cavetale.server.sql;


import com.winthier.sql.SQLRow;
import java.util.List;

public final class SQLStatic {
    public static List<Class<? extends SQLRow>> getDatabaseClasses() {
        return List.of(SQLBack.class);
    }

    private SQLStatic() { }
}
