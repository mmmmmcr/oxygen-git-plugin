package com.oxygenxml.git.service;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oxygenxml.git.options.OptionsManager;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;

public class GitAccessPullTest {
  
  /**
   * Logger for logging.
   */
  private static final Logger logger = Logger.getLogger(GitAccessPullTest.class.getName());
  
	protected final static String FIRST_LOCAL_TEST_REPOSITPRY = "target/test-resources/GitAccessPullTest/local";
	protected final static String SECOND_LOCAL_TEST_REPOSITORY = "target/test-resources/GitAccessPullTest/local2";
	private final static String REMOTE_TEST_REPOSITPRY = "target/test-resources/GitAccessPullTest/remote";
	private Repository localRepo1;
	private Repository localRepo2;
	private Repository remoteRepo;
	protected GitAccess gitAccess;

	@Before
	public void init() throws Exception {
		gitAccess = GitAccess.getInstance();
		gitAccess.createNewRepository(FIRST_LOCAL_TEST_REPOSITPRY);
		localRepo1 = gitAccess.getRepository();
		gitAccess.createNewRepository(SECOND_LOCAL_TEST_REPOSITORY);
		localRepo2 = gitAccess.getRepository();
		gitAccess.createNewRepository(REMOTE_TEST_REPOSITPRY);
		remoteRepo = gitAccess.getRepository();

		gitAccess.setRepositorySynchronously(FIRST_LOCAL_TEST_REPOSITPRY);
		File file = new File(FIRST_LOCAL_TEST_REPOSITPRY + "/test.txt");
		file.createNewFile();	
		StoredConfig config = gitAccess.getRepository().getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
		URIish uri = new URIish(remoteRepo.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		RefSpec spec1 = new RefSpec("+refs/heads/*:refs/remotes/origin/*");
    remoteConfig.addFetchRefSpec(spec1);
		remoteConfig.update(config);
		config.save();
		
    String branchName = "master";
    String remoteName = "origin";
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, remoteName);
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
    config.save();

		gitAccess.setRepositorySynchronously(SECOND_LOCAL_TEST_REPOSITORY);
		config = gitAccess.getRepository().getConfig();
		remoteConfig = new RemoteConfig(config, "origin");
		uri = new URIish(remoteRepo.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		RefSpec spec = new RefSpec("+refs/heads/*:refs/remotes/origin/*");
		remoteConfig.addFetchRefSpec(spec);
		remoteConfig.update(config);
		config.save();
		
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, remoteName);
    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
    config.save();

	}

	@Test
	public void testPullOK() throws Exception {
		pushOneFileToRemote();

		gitAccess.setRepositorySynchronously(SECOND_LOCAL_TEST_REPOSITORY);
		OptionsManager.getInstance().saveSelectedRepository(SECOND_LOCAL_TEST_REPOSITORY);
		PullResponse response = gitAccess.pull("", "");
		PullStatus actual = response.getStatus();
		PullStatus expected = PullStatus.OK;
		assertEquals(expected, actual);
	}

	@Test
	public void testPullUncomitedFiles() throws Exception {
		pushOneFileToRemote();

		gitAccess.setRepositorySynchronously(SECOND_LOCAL_TEST_REPOSITORY);
		File file = new File(SECOND_LOCAL_TEST_REPOSITORY + "/test2.txt");
		file.createNewFile();

		PullResponse response = gitAccess.pull("", "");
		PullStatus actual = response.getStatus();
		PullStatus expected = PullStatus.OK;
		assertEquals(expected, actual);
	}
	
	@Test
	public void testPullConflicts() throws Exception {
		pushOneFileToRemote();

		gitAccess.setRepositorySynchronously(SECOND_LOCAL_TEST_REPOSITORY);
		OptionsManager.getInstance().saveSelectedRepository(SECOND_LOCAL_TEST_REPOSITORY);
		File file = new File(SECOND_LOCAL_TEST_REPOSITORY + "/test.txt");
		file.createNewFile();
		
		PrintWriter out = new PrintWriter(FIRST_LOCAL_TEST_REPOSITPRY + "/test.txt");
		out.println("teeeeeest");
		out.close();
		
		gitAccess.add(new FileStatus(GitChangeType.ADD, "test.txt"));
		gitAccess.commit("conflict");
		PushResponse pushResponse = gitAccess.push("", "");
		Status pushActual = pushResponse.getStatus();
		Status pushExpected = Status.REJECTED_NONFASTFORWARD;
		assertEquals(pushExpected, pushActual);
		
		PullResponse pullResponse = gitAccess.pull("", "");
		PullStatus pullActual = pullResponse.getStatus();
		PullStatus pullExpected = PullStatus.CONFLICTS;
		assertEquals(pullExpected, pullActual);
	}
	
	@After
	public void freeResources() {
	  // JGit relies on GC to release some file handles. See org.eclipse.jgit.internal.storage.file.WindowCache.Ref
	  // When an object is collected by the GC, it releases a file lock.
    WindowCache.getInstance().cleanup();

		gitAccess.closeRepo();
		localRepo1.close();
		localRepo2.close();
		remoteRepo.close();
		File dirToDelete = new File(FIRST_LOCAL_TEST_REPOSITPRY);
		try {
			FileUtils.deleteDirectory(dirToDelete);
			dirToDelete = new File(REMOTE_TEST_REPOSITPRY);
			FileUtils.deleteDirectory(dirToDelete);
			dirToDelete = new File(SECOND_LOCAL_TEST_REPOSITORY);
			FileUtils.deleteDirectory(dirToDelete);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads LOCAL_TEST_REPOSITPRY and pushes one file to REMOTE_TEST_REPOSITPRY.
	 * 
	 * @throws Exception If it fails.
	 */
	protected void pushOneFileToRemote() throws Exception {
		gitAccess.setRepositorySynchronously(FIRST_LOCAL_TEST_REPOSITPRY);
		OptionsManager.getInstance().saveSelectedRepository(FIRST_LOCAL_TEST_REPOSITPRY);

		PrintWriter out = new PrintWriter(FIRST_LOCAL_TEST_REPOSITPRY + "/test.txt");
		out.println("hellllo");
		out.close();
		gitAccess.add(new FileStatus(GitChangeType.ADD, "test.txt"));
		gitAccess.commit("file test added");
		gitAccess.push("", "");
	}
}
