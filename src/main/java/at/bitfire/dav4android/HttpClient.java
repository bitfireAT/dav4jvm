/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import java.util.logging.Level;

import lombok.extern.slf4j.Slf4j;

public class HttpClient extends OkHttpClient {

    public HttpClient() {
        super();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String message) {
                Constants.log.trace(message);
            }
        });
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        networkInterceptors().add(logging);
    }

}
