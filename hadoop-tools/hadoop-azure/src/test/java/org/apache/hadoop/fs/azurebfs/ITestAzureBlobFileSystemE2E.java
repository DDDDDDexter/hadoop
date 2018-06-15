/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.azurebfs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;


import org.apache.hadoop.fs.azurebfs.services.AbfsServiceProviderImpl;
import org.junit.Test;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.azurebfs.contracts.services.ConfigurationService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.azurebfs.constants.ConfigurationKeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 * Test end to end between ABFS client and ABFS server.
 */
public class ITestAzureBlobFileSystemE2E extends DependencyInjectedTest {
  private static final Path TEST_FILE = new Path("testfile");
  private static final int TEST_BYTE = 100;
  private static final int TEST_OFFSET = 100;
  private static final int TEST_DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024;
  private static final int TEST_DEFAULT_READ_BUFFER_SIZE = 1023900;

  public ITestAzureBlobFileSystemE2E() {
    super();
    Configuration configuration = this.getConfiguration();
    configuration.set(ConfigurationKeys.FS_AZURE_READ_AHEAD_QUEUE_DEPTH, "0");
    this.getMockServiceInjector().replaceInstance(Configuration.class, configuration);

  }

  @Test
  public void testWriteOneByteToFile() throws Exception {
    final AzureBlobFileSystem fs = this.getFileSystem();
    FSDataOutputStream stream = fs.create(TEST_FILE);

    stream.write(TEST_BYTE);
    stream.close();

    FileStatus fileStatus = fs.getFileStatus(TEST_FILE);
    assertEquals(1, fileStatus.getLen());
  }

  @Test
  public void testReadWriteBytesToFile() throws Exception {
    final AzureBlobFileSystem fs = this.getFileSystem();
    testWriteOneByteToFile();
    FSDataInputStream inputStream = fs.open(TEST_FILE, TEST_DEFAULT_BUFFER_SIZE);
    int i = inputStream.read();
    inputStream.close();

    assertEquals(TEST_BYTE, i);
  }

  @Test (expected = IOException.class)
  public void testOOBWrites() throws Exception {
    final AzureBlobFileSystem fs = this.getFileSystem();
    int readBufferSize = AbfsServiceProviderImpl.instance().get(ConfigurationService.class).getReadBufferSize();

    fs.create(TEST_FILE);
    FSDataOutputStream writeStream = fs.create(TEST_FILE);

    byte[] bytesToRead = new byte[readBufferSize];
    final byte[] b = new byte[2 * readBufferSize];
    new Random().nextBytes(b);

    writeStream.write(b);
    writeStream.flush();
    writeStream.close();

    FSDataInputStream readStream = fs.open(TEST_FILE);
    readStream.read(bytesToRead, 0, readBufferSize);

    writeStream = fs.create(TEST_FILE);
    writeStream.write(b);
    writeStream.flush();
    writeStream.close();

    readStream.read(bytesToRead, 0, readBufferSize);
    readStream.close();
  }

  @Test
  public void testWriteWithBufferOffset() throws Exception {
    final AzureBlobFileSystem fs = this.getFileSystem();
    final FSDataOutputStream stream = fs.create(TEST_FILE);

    final byte[] b = new byte[1024 * 1000];
    new Random().nextBytes(b);
    stream.write(b, TEST_OFFSET, b.length - TEST_OFFSET);
    stream.close();

    final byte[] r = new byte[TEST_DEFAULT_READ_BUFFER_SIZE];
    FSDataInputStream inputStream = fs.open(TEST_FILE, TEST_DEFAULT_BUFFER_SIZE);
    int result = inputStream.read(r);

    assertNotEquals(-1, result);
    assertArrayEquals(r, Arrays.copyOfRange(b, TEST_OFFSET, b.length));

    inputStream.close();
  }

  @Test
  public void testReadWriteHeavyBytesToFileWithSmallerChunks() throws Exception {
    final AzureBlobFileSystem fs = this.getFileSystem();
    final FSDataOutputStream stream = fs.create(TEST_FILE);

    final byte[] writeBuffer = new byte[5 * 1000 * 1024];
    new Random().nextBytes(writeBuffer);
    stream.write(writeBuffer);
    stream.close();

    final byte[] readBuffer = new byte[5 * 1000 * 1024];
    FSDataInputStream inputStream = fs.open(TEST_FILE, TEST_DEFAULT_BUFFER_SIZE);
    int offset = 0;
    while (inputStream.read(readBuffer, offset, TEST_OFFSET) > 0) {
      offset += TEST_OFFSET;
    }

    assertArrayEquals(readBuffer, writeBuffer);
    inputStream.close();
  }
}
