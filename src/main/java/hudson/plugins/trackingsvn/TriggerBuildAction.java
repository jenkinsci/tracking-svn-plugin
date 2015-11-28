package hudson.plugins.trackingsvn;

import hudson.model.*;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionTagAction;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.*;

public class TriggerBuildAction implements Action {

	private AbstractBuild<?,?> build;

	/**
	 * Names of projects that have already been triggered
	 */
	private Set<String> triggeredProjects = new HashSet<String>();

	/**
	 * Map project -> build number for the result of triggered builds
	 */
	private Map<String,Integer> triggeredBuilds = new HashMap<String, Integer>();

	public TriggerBuildAction(AbstractBuild<?, ?> build) {
		this.build = build;
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return "Trigger Build";
	}

	public String getUrlName() {
		return "triggerBuild";
	}

	public HttpResponse doTrigger(@QueryParameter String project) throws IOException {
		build.checkPermission(Job.BUILD);

		SubversionTagAction tagAction = build.getAction(SubversionTagAction.class);
		if (tagAction == null) {
			throw new TrackingSVNException("Project " + build.getParent() + " is not an SVN project");
		}

		TriggerBuildPublisher publisher = build.getProject().getPublishersList().get(TriggerBuildPublisher.class);
		ArrayList<SubversionSCM.SvnInfo> revisions = new ArrayList<SubversionSCM.SvnInfo>();
		for (SubversionSCM.SvnInfo info: tagAction.getTags().keySet()) {
			if (!publisher.isURLIgnored(info.url)) {
				revisions.add(info);
			}
		}

		TrackingSVNRevisionParameterAction action = new TrackingSVNRevisionParameterAction(revisions, build);

		AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(project, AbstractProject.class);
		if (p == null) {
			throw new TrackingSVNException("Project " + project + " unknown.");
		}

		p.scheduleBuild2(0, new Cause.UserCause(), action, new TrackingSVNAction(build));

		triggeredProjects.add(project);

		build.save();

		return HttpResponses.forwardToPreviousPage();

	}

	public Collection<AbstractProject> getTriggerCandidateProjects() {
		Collection<AbstractProject> result = new ArrayList<AbstractProject>();

		if (!build.hasPermission(Job.BUILD)) {
			return result;
		}

		TriggerBuildPublisher publisher = build.getProject().getPublishersList().get(TriggerBuildPublisher.class);

		result.addAll(publisher.getAdditionalProjectsList());

		if (publisher.isTriggerDownstreamProjects())
		for (AbstractProject<?,?> project: Hudson.getInstance().getItems(AbstractProject.class)) {
			TrackingSVNProperty prop = project.getProperty(TrackingSVNProperty.class);
			if (prop == null) continue;

			if (prop.getSourceProject().equals(build.getProject().getName())) {
				result.add(project);
			}
		}

		return result;

	}

	public void addTriggeredBuild(AbstractBuild build) {
		triggeredBuilds.put(build.getProject().getName(), build.getNumber());
		triggeredProjects.remove(build.getProject().getName());

		try {
			build.save();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Object readResolve() {
		if (triggeredProjects == null) triggeredProjects = new HashSet<String>();
		if (triggeredBuilds == null) triggeredBuilds = new HashMap<String,Integer>();

		return this;
	}

	public AbstractBuild<?,?> getBuild() {
		return build;
	}

	public boolean isTriggered(String project) {
		return triggeredProjects.contains(project);
	}

	public AbstractBuild getTriggeredBuild(String project) {
		Integer buildNumber = triggeredBuilds.get(project);
		if (buildNumber == null) return null;
		return (AbstractBuild) Hudson.getInstance().getItemByFullName(project, AbstractProject.class).getBuildByNumber(buildNumber);
	}
}
