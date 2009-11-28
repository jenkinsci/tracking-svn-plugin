package hudson.plugins.trackingsvn;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Hudson;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class TrackingSVNAction extends InvisibleAction implements EnvironmentContributingAction {

	private final String trackedBuildProject;
	private final int trackedBuildNumber;
	
	public TrackingSVNAction(Run<?,?> build) {
		trackedBuildProject = build.getParent().getName();
		trackedBuildNumber = build.getNumber();
	}
	
	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.put("TRACKING_SVN_BUILD", getTrackedBuildURL());
	}
	
	public Run<?,?> getTrackedBuild() {
		Job<?,?> job = (Job<?,?>) Hudson.getInstance().getItem(trackedBuildProject);
		return job.getBuildByNumber(trackedBuildNumber);
	}

	@Exported(visibility=2)
	public String getTrackedBuildProject() {
		return trackedBuildProject;
	}

	@Exported(visibility=2)
	public int getTrackedBuildNumber() {
		return trackedBuildNumber;
	}
	
	@Exported(visibility=2)
	public String getTrackedBuildURL() {
		Run<?,?> r = getTrackedBuild();
		if (r == null) return null;
		return Hudson.getInstance().getRootUrl() + r.getUrl();
	}

}
