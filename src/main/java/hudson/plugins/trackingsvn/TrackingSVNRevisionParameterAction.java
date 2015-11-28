package hudson.plugins.trackingsvn;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM;

import java.util.List;

public class TrackingSVNRevisionParameterAction extends RevisionParameterAction {

	private String buildID;

	public TrackingSVNRevisionParameterAction(List<SubversionSCM.SvnInfo> revisions, AbstractBuild build) {
		super(revisions);

		this.buildID = build.getExternalizableId();
	}

	public AbstractBuild getBuild() {
		return (AbstractBuild) Run.fromExternalizableId(buildID);
	}
}
