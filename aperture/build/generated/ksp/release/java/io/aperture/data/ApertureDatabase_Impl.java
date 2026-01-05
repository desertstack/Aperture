package io.aperture.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import io.aperture.data.dao.HttpTransactionDao;
import io.aperture.data.dao.HttpTransactionDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ApertureDatabase_Impl extends ApertureDatabase {
  private volatile HttpTransactionDao _httpTransactionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `http_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `request_date` INTEGER NOT NULL, `method` TEXT NOT NULL, `url` TEXT NOT NULL, `host` TEXT NOT NULL, `path` TEXT NOT NULL, `scheme` TEXT NOT NULL, `protocol` TEXT, `request_content_type` TEXT, `request_content_length` INTEGER, `request_headers` TEXT, `request_body` TEXT, `request_body_is_plain_text` INTEGER NOT NULL, `response_date` INTEGER, `response_code` INTEGER, `response_message` TEXT, `response_content_type` TEXT, `response_content_length` INTEGER, `response_headers` TEXT, `response_body` TEXT, `response_body_is_plain_text` INTEGER NOT NULL, `duration` INTEGER, `error` TEXT, `request_payload_size` INTEGER, `response_payload_size` INTEGER, `is_gzip_encoded` INTEGER NOT NULL, `is_mocked` INTEGER NOT NULL, `mock_enabled` INTEGER NOT NULL, `mock_response_code` INTEGER, `mock_response_headers` TEXT, `mock_response_body` TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_request_date` ON `http_transactions` (`request_date`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_url` ON `http_transactions` (`url`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_method` ON `http_transactions` (`method`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_response_code` ON `http_transactions` (`response_code`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c905ba63d3a5bcb1bce9593cc4003eb8')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `http_transactions`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsHttpTransactions = new HashMap<String, TableInfo.Column>(31);
        _columnsHttpTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_date", new TableInfo.Column("request_date", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("method", new TableInfo.Column("method", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("url", new TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("host", new TableInfo.Column("host", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("path", new TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("scheme", new TableInfo.Column("scheme", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("protocol", new TableInfo.Column("protocol", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_content_type", new TableInfo.Column("request_content_type", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_content_length", new TableInfo.Column("request_content_length", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_headers", new TableInfo.Column("request_headers", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_body", new TableInfo.Column("request_body", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_body_is_plain_text", new TableInfo.Column("request_body_is_plain_text", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_date", new TableInfo.Column("response_date", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_code", new TableInfo.Column("response_code", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_message", new TableInfo.Column("response_message", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_content_type", new TableInfo.Column("response_content_type", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_content_length", new TableInfo.Column("response_content_length", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_headers", new TableInfo.Column("response_headers", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_body", new TableInfo.Column("response_body", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_body_is_plain_text", new TableInfo.Column("response_body_is_plain_text", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("duration", new TableInfo.Column("duration", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("error", new TableInfo.Column("error", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("request_payload_size", new TableInfo.Column("request_payload_size", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("response_payload_size", new TableInfo.Column("response_payload_size", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("is_gzip_encoded", new TableInfo.Column("is_gzip_encoded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("is_mocked", new TableInfo.Column("is_mocked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("mock_enabled", new TableInfo.Column("mock_enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("mock_response_code", new TableInfo.Column("mock_response_code", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("mock_response_headers", new TableInfo.Column("mock_response_headers", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHttpTransactions.put("mock_response_body", new TableInfo.Column("mock_response_body", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHttpTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHttpTransactions = new HashSet<TableInfo.Index>(4);
        _indicesHttpTransactions.add(new TableInfo.Index("idx_request_date", false, Arrays.asList("request_date"), Arrays.asList("ASC")));
        _indicesHttpTransactions.add(new TableInfo.Index("idx_url", false, Arrays.asList("url"), Arrays.asList("ASC")));
        _indicesHttpTransactions.add(new TableInfo.Index("idx_method", false, Arrays.asList("method"), Arrays.asList("ASC")));
        _indicesHttpTransactions.add(new TableInfo.Index("idx_response_code", false, Arrays.asList("response_code"), Arrays.asList("ASC")));
        final TableInfo _infoHttpTransactions = new TableInfo("http_transactions", _columnsHttpTransactions, _foreignKeysHttpTransactions, _indicesHttpTransactions);
        final TableInfo _existingHttpTransactions = TableInfo.read(db, "http_transactions");
        if (!_infoHttpTransactions.equals(_existingHttpTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "http_transactions(io.aperture.data.entity.HttpTransaction).\n"
                  + " Expected:\n" + _infoHttpTransactions + "\n"
                  + " Found:\n" + _existingHttpTransactions);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "c905ba63d3a5bcb1bce9593cc4003eb8", "a69314263734cc443a4e5c922433b702");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "http_transactions");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `http_transactions`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(HttpTransactionDao.class, HttpTransactionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public HttpTransactionDao transactionDao() {
    if (_httpTransactionDao != null) {
      return _httpTransactionDao;
    } else {
      synchronized(this) {
        if(_httpTransactionDao == null) {
          _httpTransactionDao = new HttpTransactionDao_Impl(this);
        }
        return _httpTransactionDao;
      }
    }
  }
}
