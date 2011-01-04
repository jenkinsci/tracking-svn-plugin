package hudson.plugins.trackingsvn;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.trackingsvn.TrackingSVNProperty.ToTrack;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionTagAction;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;

public class TrackingSVNTest extends HudsonTestCase {

    public void test1() throws Exception {
        File repo = new CopyExisting(getClass().getResource("svn-repo.zip")).allocate();
        SubversionSCM scm = new SubversionSCM("file://" + repo.getAbsolutePath().replace('\\', '/'));

        FreeStyleProject p1 = createFreeStyleProject();
        p1.setScm(scm);
        
        FreeStyleProject p2 = createFreeStyleProject();
        p2.setScm(scm);
        p2.addProperty(new TrackingSVNProperty(p1.getName(), ToTrack.LAST_STABLE, null));
        
        long revision1 = buildAndGetRevision(p1);
        long revision2 = buildAndGetRevision(p2);
        
        assertEquals(revision1, revision2);
        
        doCommit(scm);

        // Should still build r2 even though r3 now exists, since p1 hasn't built r3 yet.
        revision2 = buildAndGetRevision(p2);
        assertEquals(revision1, revision2);
        
        revision1 = buildAndGetRevision(p1);
        revision2 = buildAndGetRevision(p2);
        assertEquals(revision1, revision2);
        
        doCommit(scm);

        p1.getBuildersList().add(new UnstableBuilder());

        // Now p1 builds r4 but it is unstable, so p2 should still build r3.
        long newRevision1 = buildAndGetRevision(p1);
        revision2 = buildAndGetRevision(p2);
        assertFalse(newRevision1 == revision2);
        assertEquals(revision1, revision2);
    }

    public void test2() throws Exception {
        File repo = new CopyExisting(getClass().getResource("svn-repo.zip")).allocate();
        String scmUrl = "file://" + repo.getAbsolutePath().replace('\\', '/');
        SubversionSCM scm = new SubversionSCM(scmUrl);

        FreeStyleProject p1 = createFreeStyleProject();
        p1.setScm(scm);
        
        FreeStyleProject p2 = createFreeStyleProject();
        p2.setScm(scm);
        // Add property with an ignore setting
        p2.addProperty(new TrackingSVNProperty(p1.getName(), ToTrack.LAST_BUILD, scmUrl));
        
        long revision1 = buildAndGetRevision(p1);
        
        doCommit(scm);

        // Should build newest revision.. since this scmUrl is ignored, it won't track p1.
        long revision2 = buildAndGetRevision(p2);
        
        assertTrue(revision2 > revision1);
    }

    private void doCommit(SubversionSCM scm) throws IOException, Exception,
			InterruptedException, ExecutionException, SVNException {
        FreeStyleProject forCommit = createFreeStyleProject();
        forCommit.setScm(scm);
        forCommit.setAssignedLabel(hudson.getSelfLabel());
        FreeStyleBuild b = assertBuildStatusSuccess(forCommit.scheduleBuild2(0).get());
        FilePath newFile = b.getWorkspace().child("foo");
        boolean exists = newFile.exists();
        newFile.touch(System.currentTimeMillis());
        newFile.write("" + System.currentTimeMillis(), null);
        SVNClientManager svnm = SubversionSCM.createSvnClientManager(); // <-- Pass forCommit as param
                                   // when dependency is updated to newer version of subversion plugin.
        if (!exists) svnm.getWCClient().doAdd(new File(newFile.getRemote()),false,false,false, SVNDepth.INFINITY, false,false);
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(new File[]{new File(newFile.getRemote())},false,"added",null,null,false,false,SVNDepth.EMPTY);
    }

    private long buildAndGetRevision(FreeStyleProject p) throws Exception {
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(getLog(b));
		SubversionTagAction action = b.getAction(SubversionTagAction.class);
		return action.getTags().keySet().iterator().next().revision;
    }
}
