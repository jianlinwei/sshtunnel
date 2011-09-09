package org.sshtunnel;

import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;
import org.sshtunnel.utils.Constraints;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class FingerPrintDialog extends Activity {

	private Profile profile = null;
	private String fingerPrint = "";
	private String fingerPrintType = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setContentView(R.layout.layout_finger_print_dialog);
		Bundle bundle = getIntent().getExtras();
		int status = bundle.getInt(Constraints.FINGER_PRINT_STATUS);
		fingerPrint = bundle.getString(Constraints.FINGER_PRINT);
		fingerPrintType = bundle.getString(Constraints.FINGER_PRINT_TYPE);
		int profileId = bundle.getInt(Constraints.ID);

		profile = ProfileFactory.loadProfileFromDao(profileId);

		TextView warningText = (TextView) findViewById(R.id.warning_text);
		Button acceptButton = (Button) findViewById(R.id.accept);
		Button denyButton = (Button) findViewById(R.id.deny);

		acceptButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				accept();
			}
		});

		denyButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				deny();
			}
		});

		StringBuffer sb = new StringBuffer();

		switch (status) {
		case Constraints.FINGER_PRINT_INIITIALIZE:
			sb.append(getString(R.string.finger_print_first) + "\n\n");
			break;
		case Constraints.FINGER_PRINT_CHANGED:
			sb.append(getString(R.string.finger_print_changed) + "\n\n");
			break;
		}

		sb.append(getString(R.string.finger_print) + fingerPrint);

		warningText.setText(sb.toString());
	}

	private void accept() {
		if (profile != null) {
			profile.setFingerPrintType(fingerPrintType);
			profile.setFingerPrint(fingerPrint);
			ProfileFactory.saveToDao(profile);
		}
		finish();
	}

	private void deny() {
		finish();
	}

}
