package hudson.plugins.trackingsvn;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.trackingsvn.TrackingSVNProperty.ToTrack;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionTagAction;
import hudson.util.StreamTaskListener;

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
        SubversionSCM scm = new SubversionSCM("file://" + repo.getPath());

        FreeStyleProject p1 = createFreeStyleProject();
        p1.setScm(scm);
        
        FreeStyleProject p2 = createFreeStyleProject();
        p2.setScm(scm);
        p2.addProperty(new TrackingSVNProperty(p1.getName(), ToTrack.LAST_STABLE));
        
        long revision1 = getRevision(p1);
        
        long revision2 = getRevision(p2);
        
        assertEquals(revision1, revision2);
        
        doCommit(scm);
        
        revision2 = getRevision(p2);
        assertEquals(revision1, revision2);
        
        revision1 = getRevision(p1);
        revision2 = getRevision(p2);
        assertEquals(revision1, revision2);
        
        doCommit(scm);

        p1.getBuildersList().add(new UnstableBuilder());
        
        long newRevision1 = getRevision(p1);
        revision2 = getRevision(p2);
        assertFalse(newRevision1 == revision2);
        assertEquals(revision1, revision2);

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
        SVNClientManager svnm = SubversionSCM.createSvnClientManager();
        if (!exists) svnm.getWCClient().doAdd(new File(newFile.getRemote()),false,false,false, SVNDepth.INFINITY, false,false);
        SVNCommitClient cc = svnm.getCommitClient();
        cc.doCommit(new File[]{new File(newFile.getRemote())},false,"added",null,null,false,false,SVNDepth.EMPTY);
	}
    
    

	private long getRevision(FreeStyleProject p) throws Exception {
        FreeStyleBuild b = p.scheduleBuild2(0).get();
		return b.getAction(SubversionTagAction.class).getTags().keySet().iterator().next().revision;
	}	
}
