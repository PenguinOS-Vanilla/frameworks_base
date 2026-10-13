/*
 * Copyright (C) 2026 PenguinOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.wm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Slog;

/**
 * Keeps the Pentas session service bound while a game is in front. Pentas runs one session
 * per bind of {@code pentas://session/<package>?mode=<n>} and ends it when the bind is
 * released.
 *
 * Every method and every connection callback runs on the handler given at construction, and
 * nothing here is allowed to throw: Pentas being absent or broken must not reach the caller.
 */
class PentasSessionBinder {

    private static final String TAG = "PentasSessionBinder";

    private static final ComponentName SESSION_SERVICE = new ComponentName(
            "org.yasvara.pentas", "org.yasvara.pentas.session.SessionService");
    private static final String MODE_GAME = "1";
    private static final int BIND_FLAGS = Context.BIND_AUTO_CREATE
            | Context.BIND_FOREGROUND_SERVICE | Context.BIND_IMPORTANT;

    private final Context mContext;
    private final Handler mHandler;

    private Connection mConnection;

    PentasSessionBinder(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    /** Starts a session for {@code packageName}, replacing the current one if there is any. */
    void start(String packageName) {
        if (packageName == null) return;
        final Connection previous = mConnection;
        if (previous != null && packageName.equals(previous.mPackageName)) return;

        // Pentas expects the next session to be bound before the previous one is released.
        mConnection = bind(packageName, true /* rebindOnDeath */);
        unbind(previous);
    }

    /** Ends the current session. */
    void stop() {
        final Connection connection = mConnection;
        mConnection = null;
        unbind(connection);
    }

    private Connection bind(String packageName, boolean rebindOnDeath) {
        final Connection connection = new Connection(packageName, rebindOnDeath);
        try {
            final Intent intent = new Intent()
                    .setComponent(SESSION_SERVICE)
                    .setData(new Uri.Builder()
                            .scheme("pentas")
                            .authority("session")
                            .appendPath(packageName)
                            .appendQueryParameter("mode", MODE_GAME)
                            .build());
            if (mContext.bindServiceAsUser(intent, connection, BIND_FLAGS, mHandler,
                    UserHandle.CURRENT)) {
                return connection;
            }
            Slog.w(TAG, "Pentas is not available for " + packageName);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to bind a session for " + packageName, e);
        }
        // A bind that did not take still has to be released.
        try {
            mContext.unbindService(connection);
        } catch (Exception e) {
            // Never registered.
        }
        return null;
    }

    private void unbind(Connection connection) {
        if (connection == null) return;
        try {
            mContext.unbindService(connection);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to unbind the session for " + connection.mPackageName, e);
        }
    }

    private final class Connection implements ServiceConnection {

        final String mPackageName;
        private final boolean mRebindOnDeath;

        Connection(String packageName, boolean rebindOnDeath) {
            mPackageName = packageName;
            mRebindOnDeath = rebindOnDeath;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The system restarts the service and binds it again by itself.
        }

        @Override
        public void onBindingDied(ComponentName name) {
            if (mConnection != this) return;

            // Pentas was replaced or disabled under us and this binding will not come back.
            // Try once more, then leave it until the next game starts.
            mConnection = null;
            unbind(this);
            if (mRebindOnDeath) {
                mConnection = bind(mPackageName, false /* rebindOnDeath */);
            }
        }
    }
}
