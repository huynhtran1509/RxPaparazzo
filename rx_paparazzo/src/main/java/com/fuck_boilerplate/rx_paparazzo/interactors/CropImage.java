/*
 * Copyright 2016 FuckBoilerplate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fuck_boilerplate.rx_paparazzo.interactors;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.fuck_boilerplate.rx_paparazzo.entities.Config;
import com.fuck_boilerplate.rx_paparazzo.entities.Options;
import com.fuck_boilerplate.rx_paparazzo.entities.TargetUi;
import com.yalantis.ucrop.UCrop;

import java.io.File;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

public final class CropImage extends UseCase<Uri> {
    private final Config config;
    private final StartIntent startIntent;
    private final GetPath getPath;
    private final TargetUi targetUi;
    private final ImageUtils imageUtils;
    private Uri uri;

    public CropImage(TargetUi targetUi, Config config, StartIntent startIntent, GetPath getPath,
                     ImageUtils imageUtils) {
        this.targetUi = targetUi;
        this.config = config;
        this.startIntent = startIntent;
        this.getPath = getPath;
        this.imageUtils = imageUtils;
    }

    public CropImage with(Uri uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public Observable<Uri> react() {
        if (config.doCrop()) {
            return getIntent().flatMap(new Func1<Intent, Observable<Uri>>() {
                @Override
                public Observable<Uri> call(Intent intent) {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    return startIntent.with(intent).react().map(new Func1<Intent, Uri>() {
                        @Override
                        public Uri call(Intent intentResult) {
                            return UCrop.getOutput(intentResult);
                        }
                    });
                }
            });
        }

        return getOutputUriNoCrop();
    }

    private Observable<Intent> getIntent() {
        return Observable.zip(getInputUri(), getOutputUriCrop(),
                new Func2<Uri, Uri, Intent>() {
                    @Override
                    public Intent call(Uri uri, Uri outputUri) {
                        UCrop.Options options = config.getOptions();
                        if (options == null)
                            return UCrop.of(uri, outputUri).getIntent(targetUi.getContext());

                        if (options instanceof Options) {
                            return getIntentWithOptions((Options) options, outputUri);
                        } else {
                            return UCrop.of(uri, outputUri).withOptions(config.getOptions())
                                    .getIntent(targetUi.getContext());
                        }
                    }
                });
    }

    private Intent getIntentWithOptions(Options options, Uri outputUri) {
        UCrop uCrop = UCrop.of(uri, outputUri);

        uCrop = uCrop.withOptions(options);
        if (options.getX() != 0) uCrop = uCrop.withAspectRatio(options.getX(), options.getY());
        if (options.getWidth() != 0)
            uCrop = uCrop.withMaxResultSize(options.getWidth(), options.getHeight());

        return uCrop.getIntent(targetUi.getContext());
    }

    private Observable<Uri> getInputUri() {
        return getPath.with(uri).react().map(new Func1<String, Uri>() {
            @Override
            public Uri call(String filePath) {
                return Uri.fromFile(new File(filePath)).buildUpon().build();
            }
        });
    }

    private Observable<Uri> getOutputUriCrop() {
        return getPath.with(uri).react()
                .flatMap(new Func1<String, Observable<Uri>>() {
                    @Override
                    public Observable<Uri> call(String filepath) {
                        String extension = imageUtils.getFileExtension(filepath);
                        String filename = Constants.CROP_APPEND + extension;
                        File file = imageUtils.getPrivateFile(filename);
                        return Observable.just(Uri.fromFile(file).buildUpon().build());
                    }
                });
    }

    private Observable<Uri> getOutputUriNoCrop() {
        return getPath.with(uri).react()
                .flatMap(new Func1<String, Observable<Uri>>() {
                    @Override
                    public Observable<Uri> call(String filepath) {
                        String extension = imageUtils.getFileExtension(filepath);
                        String filename = Constants.NO_CROP_APPEND + extension;
                        File file = imageUtils.getPrivateFile(filename);
                        imageUtils.copyFile(filepath, file.getAbsolutePath());
                        return Observable.just(Uri.fromFile(file).buildUpon().build());
                    }
                });
    }
}
