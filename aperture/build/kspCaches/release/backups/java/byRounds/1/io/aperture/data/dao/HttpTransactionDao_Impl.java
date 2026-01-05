package io.aperture.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import io.aperture.data.entity.HttpTransaction;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class HttpTransactionDao_Impl implements HttpTransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HttpTransaction> __insertionAdapterOfHttpTransaction;

  private final EntityDeletionOrUpdateAdapter<HttpTransaction> __updateAdapterOfHttpTransaction;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMockStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMockResponse;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOldest;

  public HttpTransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHttpTransaction = new EntityInsertionAdapter<HttpTransaction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `http_transactions` (`id`,`request_date`,`method`,`url`,`host`,`path`,`scheme`,`protocol`,`request_content_type`,`request_content_length`,`request_headers`,`request_body`,`request_body_is_plain_text`,`response_date`,`response_code`,`response_message`,`response_content_type`,`response_content_length`,`response_headers`,`response_body`,`response_body_is_plain_text`,`duration`,`error`,`request_payload_size`,`response_payload_size`,`is_gzip_encoded`,`is_mocked`,`mock_enabled`,`mock_response_code`,`mock_response_headers`,`mock_response_body`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HttpTransaction entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRequestDate());
        statement.bindString(3, entity.getMethod());
        statement.bindString(4, entity.getUrl());
        statement.bindString(5, entity.getHost());
        statement.bindString(6, entity.getPath());
        statement.bindString(7, entity.getScheme());
        if (entity.getProtocol() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getProtocol());
        }
        if (entity.getRequestContentType() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRequestContentType());
        }
        if (entity.getRequestContentLength() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getRequestContentLength());
        }
        if (entity.getRequestHeaders() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getRequestHeaders());
        }
        if (entity.getRequestBody() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getRequestBody());
        }
        final int _tmp = entity.getRequestBodyIsPlainText() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getResponseDate() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getResponseDate());
        }
        if (entity.getResponseCode() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getResponseCode());
        }
        if (entity.getResponseMessage() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getResponseMessage());
        }
        if (entity.getResponseContentType() == null) {
          statement.bindNull(17);
        } else {
          statement.bindString(17, entity.getResponseContentType());
        }
        if (entity.getResponseContentLength() == null) {
          statement.bindNull(18);
        } else {
          statement.bindLong(18, entity.getResponseContentLength());
        }
        if (entity.getResponseHeaders() == null) {
          statement.bindNull(19);
        } else {
          statement.bindString(19, entity.getResponseHeaders());
        }
        if (entity.getResponseBody() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getResponseBody());
        }
        final int _tmp_1 = entity.getResponseBodyIsPlainText() ? 1 : 0;
        statement.bindLong(21, _tmp_1);
        if (entity.getDuration() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getDuration());
        }
        if (entity.getError() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getError());
        }
        if (entity.getRequestPayloadSize() == null) {
          statement.bindNull(24);
        } else {
          statement.bindLong(24, entity.getRequestPayloadSize());
        }
        if (entity.getResponsePayloadSize() == null) {
          statement.bindNull(25);
        } else {
          statement.bindLong(25, entity.getResponsePayloadSize());
        }
        final int _tmp_2 = entity.isGzipEncoded() ? 1 : 0;
        statement.bindLong(26, _tmp_2);
        final int _tmp_3 = entity.isMocked() ? 1 : 0;
        statement.bindLong(27, _tmp_3);
        final int _tmp_4 = entity.getMockEnabled() ? 1 : 0;
        statement.bindLong(28, _tmp_4);
        if (entity.getMockResponseCode() == null) {
          statement.bindNull(29);
        } else {
          statement.bindLong(29, entity.getMockResponseCode());
        }
        if (entity.getMockResponseHeaders() == null) {
          statement.bindNull(30);
        } else {
          statement.bindString(30, entity.getMockResponseHeaders());
        }
        if (entity.getMockResponseBody() == null) {
          statement.bindNull(31);
        } else {
          statement.bindString(31, entity.getMockResponseBody());
        }
      }
    };
    this.__updateAdapterOfHttpTransaction = new EntityDeletionOrUpdateAdapter<HttpTransaction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `http_transactions` SET `id` = ?,`request_date` = ?,`method` = ?,`url` = ?,`host` = ?,`path` = ?,`scheme` = ?,`protocol` = ?,`request_content_type` = ?,`request_content_length` = ?,`request_headers` = ?,`request_body` = ?,`request_body_is_plain_text` = ?,`response_date` = ?,`response_code` = ?,`response_message` = ?,`response_content_type` = ?,`response_content_length` = ?,`response_headers` = ?,`response_body` = ?,`response_body_is_plain_text` = ?,`duration` = ?,`error` = ?,`request_payload_size` = ?,`response_payload_size` = ?,`is_gzip_encoded` = ?,`is_mocked` = ?,`mock_enabled` = ?,`mock_response_code` = ?,`mock_response_headers` = ?,`mock_response_body` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HttpTransaction entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRequestDate());
        statement.bindString(3, entity.getMethod());
        statement.bindString(4, entity.getUrl());
        statement.bindString(5, entity.getHost());
        statement.bindString(6, entity.getPath());
        statement.bindString(7, entity.getScheme());
        if (entity.getProtocol() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getProtocol());
        }
        if (entity.getRequestContentType() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRequestContentType());
        }
        if (entity.getRequestContentLength() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getRequestContentLength());
        }
        if (entity.getRequestHeaders() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getRequestHeaders());
        }
        if (entity.getRequestBody() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getRequestBody());
        }
        final int _tmp = entity.getRequestBodyIsPlainText() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getResponseDate() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getResponseDate());
        }
        if (entity.getResponseCode() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getResponseCode());
        }
        if (entity.getResponseMessage() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getResponseMessage());
        }
        if (entity.getResponseContentType() == null) {
          statement.bindNull(17);
        } else {
          statement.bindString(17, entity.getResponseContentType());
        }
        if (entity.getResponseContentLength() == null) {
          statement.bindNull(18);
        } else {
          statement.bindLong(18, entity.getResponseContentLength());
        }
        if (entity.getResponseHeaders() == null) {
          statement.bindNull(19);
        } else {
          statement.bindString(19, entity.getResponseHeaders());
        }
        if (entity.getResponseBody() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getResponseBody());
        }
        final int _tmp_1 = entity.getResponseBodyIsPlainText() ? 1 : 0;
        statement.bindLong(21, _tmp_1);
        if (entity.getDuration() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getDuration());
        }
        if (entity.getError() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getError());
        }
        if (entity.getRequestPayloadSize() == null) {
          statement.bindNull(24);
        } else {
          statement.bindLong(24, entity.getRequestPayloadSize());
        }
        if (entity.getResponsePayloadSize() == null) {
          statement.bindNull(25);
        } else {
          statement.bindLong(25, entity.getResponsePayloadSize());
        }
        final int _tmp_2 = entity.isGzipEncoded() ? 1 : 0;
        statement.bindLong(26, _tmp_2);
        final int _tmp_3 = entity.isMocked() ? 1 : 0;
        statement.bindLong(27, _tmp_3);
        final int _tmp_4 = entity.getMockEnabled() ? 1 : 0;
        statement.bindLong(28, _tmp_4);
        if (entity.getMockResponseCode() == null) {
          statement.bindNull(29);
        } else {
          statement.bindLong(29, entity.getMockResponseCode());
        }
        if (entity.getMockResponseHeaders() == null) {
          statement.bindNull(30);
        } else {
          statement.bindString(30, entity.getMockResponseHeaders());
        }
        if (entity.getMockResponseBody() == null) {
          statement.bindNull(31);
        } else {
          statement.bindString(31, entity.getMockResponseBody());
        }
        statement.bindLong(32, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateMockStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE http_transactions SET mock_enabled = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateMockResponse = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE http_transactions\n"
                + "        SET mock_response_code = ?,\n"
                + "            mock_response_headers = ?,\n"
                + "            mock_response_body = ?,\n"
                + "            is_mocked = 1\n"
                + "        WHERE id = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM http_transactions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM http_transactions";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM http_transactions WHERE request_date < ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOldest = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        DELETE FROM http_transactions\n"
                + "        WHERE id IN (\n"
                + "            SELECT id FROM http_transactions\n"
                + "            ORDER BY request_date ASC\n"
                + "            LIMIT ?\n"
                + "        )\n"
                + "    ";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final HttpTransaction transaction,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfHttpTransaction.insertAndReturnId(transaction);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final HttpTransaction transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfHttpTransaction.handle(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMockStatus(final long id, final boolean enabled,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMockStatus.acquire();
        int _argIndex = 1;
        final int _tmp = enabled ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateMockStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMockResponse(final long id, final int responseCode, final String headers,
      final String body, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMockResponse.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, responseCode);
        _argIndex = 2;
        if (headers == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, headers);
        }
        _argIndex = 3;
        if (body == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, body);
        }
        _argIndex = 4;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateMockResponse.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOlderThan(final long beforeDate,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, beforeDate);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteOldest(final int count, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOldest.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, count);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOldest.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final int limit, final int offset,
      final Continuation<? super List<HttpTransaction>> $completion) {
    final String _sql = "SELECT * FROM http_transactions ORDER BY request_date DESC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 2;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HttpTransaction>> getAllAsFlow() {
    final String _sql = "SELECT * FROM http_transactions ORDER BY request_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"http_transactions"}, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final long id, final Continuation<? super HttpTransaction> $completion) {
    final String _sql = "SELECT * FROM http_transactions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<HttpTransaction>() {
      @Override
      @Nullable
      public HttpTransaction call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final HttpTransaction _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _result = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object searchByUrl(final String searchQuery, final int limit, final int offset,
      final Continuation<? super List<HttpTransaction>> $completion) {
    final String _sql = "SELECT * FROM http_transactions WHERE url LIKE '%' || ? || '%' ORDER BY request_date DESC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, searchQuery);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 3;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object filterByMethod(final String method, final int limit, final int offset,
      final Continuation<? super List<HttpTransaction>> $completion) {
    final String _sql = "SELECT * FROM http_transactions WHERE method = ? ORDER BY request_date DESC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, method);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 3;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object filterByStatusCode(final int statusCode, final int limit, final int offset,
      final Continuation<? super List<HttpTransaction>> $completion) {
    final String _sql = "SELECT * FROM http_transactions WHERE response_code = ? ORDER BY request_date DESC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, statusCode);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 3;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByDateRange(final long startDate, final long endDate,
      final Continuation<? super List<HttpTransaction>> $completion) {
    final String _sql = "SELECT * FROM http_transactions WHERE request_date BETWEEN ? AND ? ORDER BY request_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startDate);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endDate);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HttpTransaction>>() {
      @Override
      @NonNull
      public List<HttpTransaction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final List<HttpTransaction> _result = new ArrayList<HttpTransaction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HttpTransaction _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _item = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getMockForUrl(final String url,
      final Continuation<? super HttpTransaction> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM http_transactions\n"
            + "        WHERE url = ?\n"
            + "        AND mock_enabled = 1\n"
            + "        ORDER BY id DESC\n"
            + "        LIMIT 1\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, url);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<HttpTransaction>() {
      @Override
      @Nullable
      public HttpTransaction call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRequestDate = CursorUtil.getColumnIndexOrThrow(_cursor, "request_date");
          final int _cursorIndexOfMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "method");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfScheme = CursorUtil.getColumnIndexOrThrow(_cursor, "scheme");
          final int _cursorIndexOfProtocol = CursorUtil.getColumnIndexOrThrow(_cursor, "protocol");
          final int _cursorIndexOfRequestContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_type");
          final int _cursorIndexOfRequestContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "request_content_length");
          final int _cursorIndexOfRequestHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "request_headers");
          final int _cursorIndexOfRequestBody = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body");
          final int _cursorIndexOfRequestBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "request_body_is_plain_text");
          final int _cursorIndexOfResponseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "response_date");
          final int _cursorIndexOfResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "response_code");
          final int _cursorIndexOfResponseMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "response_message");
          final int _cursorIndexOfResponseContentType = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_type");
          final int _cursorIndexOfResponseContentLength = CursorUtil.getColumnIndexOrThrow(_cursor, "response_content_length");
          final int _cursorIndexOfResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "response_headers");
          final int _cursorIndexOfResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body");
          final int _cursorIndexOfResponseBodyIsPlainText = CursorUtil.getColumnIndexOrThrow(_cursor, "response_body_is_plain_text");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfRequestPayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "request_payload_size");
          final int _cursorIndexOfResponsePayloadSize = CursorUtil.getColumnIndexOrThrow(_cursor, "response_payload_size");
          final int _cursorIndexOfIsGzipEncoded = CursorUtil.getColumnIndexOrThrow(_cursor, "is_gzip_encoded");
          final int _cursorIndexOfIsMocked = CursorUtil.getColumnIndexOrThrow(_cursor, "is_mocked");
          final int _cursorIndexOfMockEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_enabled");
          final int _cursorIndexOfMockResponseCode = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_code");
          final int _cursorIndexOfMockResponseHeaders = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_headers");
          final int _cursorIndexOfMockResponseBody = CursorUtil.getColumnIndexOrThrow(_cursor, "mock_response_body");
          final HttpTransaction _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRequestDate;
            _tmpRequestDate = _cursor.getLong(_cursorIndexOfRequestDate);
            final String _tmpMethod;
            _tmpMethod = _cursor.getString(_cursorIndexOfMethod);
            final String _tmpUrl;
            _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpScheme;
            _tmpScheme = _cursor.getString(_cursorIndexOfScheme);
            final String _tmpProtocol;
            if (_cursor.isNull(_cursorIndexOfProtocol)) {
              _tmpProtocol = null;
            } else {
              _tmpProtocol = _cursor.getString(_cursorIndexOfProtocol);
            }
            final String _tmpRequestContentType;
            if (_cursor.isNull(_cursorIndexOfRequestContentType)) {
              _tmpRequestContentType = null;
            } else {
              _tmpRequestContentType = _cursor.getString(_cursorIndexOfRequestContentType);
            }
            final Long _tmpRequestContentLength;
            if (_cursor.isNull(_cursorIndexOfRequestContentLength)) {
              _tmpRequestContentLength = null;
            } else {
              _tmpRequestContentLength = _cursor.getLong(_cursorIndexOfRequestContentLength);
            }
            final String _tmpRequestHeaders;
            if (_cursor.isNull(_cursorIndexOfRequestHeaders)) {
              _tmpRequestHeaders = null;
            } else {
              _tmpRequestHeaders = _cursor.getString(_cursorIndexOfRequestHeaders);
            }
            final String _tmpRequestBody;
            if (_cursor.isNull(_cursorIndexOfRequestBody)) {
              _tmpRequestBody = null;
            } else {
              _tmpRequestBody = _cursor.getString(_cursorIndexOfRequestBody);
            }
            final boolean _tmpRequestBodyIsPlainText;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRequestBodyIsPlainText);
            _tmpRequestBodyIsPlainText = _tmp != 0;
            final Long _tmpResponseDate;
            if (_cursor.isNull(_cursorIndexOfResponseDate)) {
              _tmpResponseDate = null;
            } else {
              _tmpResponseDate = _cursor.getLong(_cursorIndexOfResponseDate);
            }
            final Integer _tmpResponseCode;
            if (_cursor.isNull(_cursorIndexOfResponseCode)) {
              _tmpResponseCode = null;
            } else {
              _tmpResponseCode = _cursor.getInt(_cursorIndexOfResponseCode);
            }
            final String _tmpResponseMessage;
            if (_cursor.isNull(_cursorIndexOfResponseMessage)) {
              _tmpResponseMessage = null;
            } else {
              _tmpResponseMessage = _cursor.getString(_cursorIndexOfResponseMessage);
            }
            final String _tmpResponseContentType;
            if (_cursor.isNull(_cursorIndexOfResponseContentType)) {
              _tmpResponseContentType = null;
            } else {
              _tmpResponseContentType = _cursor.getString(_cursorIndexOfResponseContentType);
            }
            final Long _tmpResponseContentLength;
            if (_cursor.isNull(_cursorIndexOfResponseContentLength)) {
              _tmpResponseContentLength = null;
            } else {
              _tmpResponseContentLength = _cursor.getLong(_cursorIndexOfResponseContentLength);
            }
            final String _tmpResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfResponseHeaders)) {
              _tmpResponseHeaders = null;
            } else {
              _tmpResponseHeaders = _cursor.getString(_cursorIndexOfResponseHeaders);
            }
            final String _tmpResponseBody;
            if (_cursor.isNull(_cursorIndexOfResponseBody)) {
              _tmpResponseBody = null;
            } else {
              _tmpResponseBody = _cursor.getString(_cursorIndexOfResponseBody);
            }
            final boolean _tmpResponseBodyIsPlainText;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfResponseBodyIsPlainText);
            _tmpResponseBodyIsPlainText = _tmp_1 != 0;
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final Long _tmpRequestPayloadSize;
            if (_cursor.isNull(_cursorIndexOfRequestPayloadSize)) {
              _tmpRequestPayloadSize = null;
            } else {
              _tmpRequestPayloadSize = _cursor.getLong(_cursorIndexOfRequestPayloadSize);
            }
            final Long _tmpResponsePayloadSize;
            if (_cursor.isNull(_cursorIndexOfResponsePayloadSize)) {
              _tmpResponsePayloadSize = null;
            } else {
              _tmpResponsePayloadSize = _cursor.getLong(_cursorIndexOfResponsePayloadSize);
            }
            final boolean _tmpIsGzipEncoded;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsGzipEncoded);
            _tmpIsGzipEncoded = _tmp_2 != 0;
            final boolean _tmpIsMocked;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsMocked);
            _tmpIsMocked = _tmp_3 != 0;
            final boolean _tmpMockEnabled;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfMockEnabled);
            _tmpMockEnabled = _tmp_4 != 0;
            final Integer _tmpMockResponseCode;
            if (_cursor.isNull(_cursorIndexOfMockResponseCode)) {
              _tmpMockResponseCode = null;
            } else {
              _tmpMockResponseCode = _cursor.getInt(_cursorIndexOfMockResponseCode);
            }
            final String _tmpMockResponseHeaders;
            if (_cursor.isNull(_cursorIndexOfMockResponseHeaders)) {
              _tmpMockResponseHeaders = null;
            } else {
              _tmpMockResponseHeaders = _cursor.getString(_cursorIndexOfMockResponseHeaders);
            }
            final String _tmpMockResponseBody;
            if (_cursor.isNull(_cursorIndexOfMockResponseBody)) {
              _tmpMockResponseBody = null;
            } else {
              _tmpMockResponseBody = _cursor.getString(_cursorIndexOfMockResponseBody);
            }
            _result = new HttpTransaction(_tmpId,_tmpRequestDate,_tmpMethod,_tmpUrl,_tmpHost,_tmpPath,_tmpScheme,_tmpProtocol,_tmpRequestContentType,_tmpRequestContentLength,_tmpRequestHeaders,_tmpRequestBody,_tmpRequestBodyIsPlainText,_tmpResponseDate,_tmpResponseCode,_tmpResponseMessage,_tmpResponseContentType,_tmpResponseContentLength,_tmpResponseHeaders,_tmpResponseBody,_tmpResponseBodyIsPlainText,_tmpDuration,_tmpError,_tmpRequestPayloadSize,_tmpResponsePayloadSize,_tmpIsGzipEncoded,_tmpIsMocked,_tmpMockEnabled,_tmpMockResponseCode,_tmpMockResponseHeaders,_tmpMockResponseBody);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM http_transactions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getMockedCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM http_transactions WHERE mock_enabled = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getFailedCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM http_transactions WHERE error IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAverageDuration(final Continuation<? super Long> $completion) {
    final String _sql = "SELECT AVG(duration) FROM http_transactions WHERE duration IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final Long _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getExcessCount(final int maxRecords,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) - ? FROM http_transactions WHERE (SELECT COUNT(*) FROM http_transactions) > ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, maxRecords);
    _argIndex = 2;
    _statement.bindLong(_argIndex, maxRecords);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
