/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 */
package org.nuxeo.lib.stream.tests.pattern;

import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.chronicle.ChronicleLogManager;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

public class TestPatternQueuingChronicle extends TestPatternQueuing {
    protected final static String OS = System.getProperty("os.name").toLowerCase();

    public final static boolean IS_WIN = OS.startsWith("win");

    protected Path basePath;

    @Before
    public void skipWindowsThatDontCleanTempFolder() {
        assumeFalse(IS_WIN);
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder(new File(FeaturesRunner.getBuildDirectory()));

    @After
    public void resetBasePath() throws IOException {
        basePath = null;
    }

    @Override
    public LogManager createManager() throws Exception {
        if (basePath == null) {
            basePath = folder.newFolder().toPath();
        }
        return new ChronicleLogManager(basePath);
    }

    @Override
    @Test
    @Ignore("NXP-23710")
    public void killConsumers() throws Exception {
        super.killConsumers();
    }
}
