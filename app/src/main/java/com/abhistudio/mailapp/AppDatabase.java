package com.abhistudio.mailapp;

import com.raizlabs.android.dbflow.annotation.Database;

//import com.dbflow5.annotation.Database;

@Database(name = AppDatabase.NAME, version = AppDatabase.VERSION)
public class AppDatabase {

    public static final String NAME = "AppDatabase";

    public static final int VERSION = 1;
}