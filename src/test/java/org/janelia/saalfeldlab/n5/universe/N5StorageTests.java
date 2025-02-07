package org.janelia.saalfeldlab.n5.universe;

import com.amazonaws.services.s3.AmazonS3;
import com.google.cloud.storage.Storage;
import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.n5.AbstractN5Test;
import org.janelia.saalfeldlab.n5.FileSystemKeyValueAccess;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.GoogleCloudStorageKeyValueAccess;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageTests;
import org.janelia.saalfeldlab.n5.googlecloud.backend.BackendGoogleCloudStorageFactory;
import org.janelia.saalfeldlab.n5.googlecloud.mock.MockGoogleCloudStorageFactory;
import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Tests;
import org.janelia.saalfeldlab.n5.s3.mock.MockS3Factory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.janelia.saalfeldlab.n5.s3.N5AmazonS3Tests.tempBucketName;
import static org.janelia.saalfeldlab.n5.s3.N5AmazonS3Tests.tempContainerPath;
import static org.junit.Assert.assertTrue;

@RunWith(Suite.class)
@Suite.SuiteClasses({N5StorageTests.N5FileSystemTest.class, N5StorageTests.N5AmazonS3MockTest.class, N5StorageTests.N5GoogleCloudMockTest.class})
public class N5StorageTests {

	public static abstract class N5FactoryTest extends AbstractN5Test implements StorageSchemeWrappedN5Test {

		protected N5Factory factory;

		protected final ArrayList<N5Writer> tempWriters = new ArrayList<>();

		public N5FactoryTest() {

			this.factory = getFactory();
		}

		@Override abstract protected String tempN5Location();

		@Override public N5Factory getFactory() {

			if (factory == null) {
				factory = new N5Factory();
			}
			return factory;
		}

		@Override public StorageFormat getStorageFormat() {

			return StorageFormat.N5;
		}

		@Override protected N5Reader createN5Reader(String location, GsonBuilder gson) {

			factory.gsonBuilder(gson);
			return createN5Writer(location);
		}

		@Override protected N5Reader createN5Reader(String location) {

			return getReader(location);
		}

		@Override protected N5Writer createN5Writer(String location, GsonBuilder gson) {

			factory.gsonBuilder(gson);
			return createN5Writer(location);
		}

		@Override protected N5Writer createN5Writer(String location) {

			return getWriter(location);
		}

		@After
		public void removeTempWriter() {

			synchronized (tempWriters) {

				for (N5Writer tempWriter : tempWriters) {
					tempWriter.remove();
				}
				tempWriters.clear();
			}
		}
	}

	public static class N5FileSystemTest extends N5FactoryTest {

		@Override public Class<?> getBackendTargetClass() {

			return FileSystemKeyValueAccess.class;
		}

		@Override protected String tempN5Location() {

			try {
				return Files.createTempDirectory("n5-test").toUri().getPath();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static abstract class N5AmazonS3FactoryTest extends N5FactoryTest {

		public static AmazonS3 s3 = null;
		final static String testBucket = tempBucketName();

		final static N5Factory FACTORY = new N5Factory() {

			@Override AmazonS3 createS3(String uri) {

				return s3 != null ? s3 : (s3 = super.createS3(uri));
			}
		};

		@Override public Class<?> getBackendTargetClass() {

			return AmazonS3KeyValueAccess.class;
		}

		@AfterClass
		public static void removeTestBucket() {
			try {
				FACTORY.openWriter("s3://" + testBucket).remove("");
			} catch (Throwable e) {
				System.err.println("Error during test cleanup. Bucket " + testBucket + " may still exist.");
			}
		}

		@Override public N5Factory getFactory() {

			return factory != null ? factory : (factory = FACTORY);
		}

		@Override protected String tempN5Location() {

			try {
				return new URI("s3", testBucket, tempContainerPath(), null).toString();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class N5AmazonS3MockTest extends N5AmazonS3FactoryTest {
		public N5AmazonS3MockTest() {

			N5AmazonS3FactoryTest.s3 = MockS3Factory.getOrCreateS3();
		}

		@Override protected String tempN5Location() {

			try {
				return new URI("http", "localhost:8001", "/" + testBucket + tempContainerPath(), null, null).toString();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class N5AmazonS3BackendTest extends N5AmazonS3FactoryTest {

		@BeforeClass
		public static void ensureBucketExists() {

			final N5Writer writer = FACTORY.createWriter("s3://" + testBucket + "/" + tempContainerPath());
			assertTrue(writer.exists(""));
			writer.remove();
		}

		@Rule public TestWatcher skipIfErroneousFailure = new N5AmazonS3Tests.SkipErroneousNoSuchBucketFailure();

		public N5AmazonS3BackendTest() {

			N5AmazonS3FactoryTest.s3 = null;
		}
	}

	public static abstract class N5GoogleCloudFactoryTest extends N5FactoryTest {

		protected static String testBucket = N5GoogleCloudStorageTests.tempBucketName();
		protected static Storage storage = null;
		protected static N5Factory FACTORY = new N5Factory() {

			@Override Storage createGoogleCloudStorage() {

				return storage;
			}
		};

		@Override public Class<?> getBackendTargetClass() {

			return GoogleCloudStorageKeyValueAccess.class;
		}

		@AfterClass
		public static void removeTestBucket() {
			try {
				FACTORY.openWriter("gs://" + testBucket).remove("");
			} catch (Throwable e) {
				System.err.println("Error during test cleanup. Bucket " + testBucket + " may still exist.");
			}
		}

		@Override public N5Factory getFactory() {

			return factory != null ? factory : (factory = FACTORY);
		}

		@Override protected String tempN5Location() {

			try {
				return new URI("gs", testBucket, tempContainerPath(), null).toString();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class N5GoogleCloudMockTest extends N5GoogleCloudFactoryTest {
		public N5GoogleCloudMockTest() {

			N5GoogleCloudFactoryTest.storage = MockGoogleCloudStorageFactory.getOrCreateStorage();
		}
	}

	public static class N5GoogleCloudBackendTest extends N5GoogleCloudFactoryTest {

		@BeforeClass
		public static void ensureBucketExists() {

			final N5Writer writer = N5Factory.createWriter("gs://" + testBucket + "/" + tempContainerPath());
			assertTrue(writer.exists(""));
			writer.remove();
		}

		public N5GoogleCloudBackendTest() {

			N5GoogleCloudFactoryTest.storage = BackendGoogleCloudStorageFactory.getOrCreateStorage();
		}

		@Override public void testVersion() throws NumberFormatException, IOException, URISyntaxException {

			super.testVersion();
		}
	}
}
