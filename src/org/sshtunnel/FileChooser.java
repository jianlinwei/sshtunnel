package org.sshtunnel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class FileChooser extends ListActivity {

	private File currentDir;
	private FileArrayAdapter adapter;

	private void fill(File f) {
		File[] dirs = f.listFiles();
		this.setTitle(getString(R.string.current_dir) + ": " + f.getName());
		List<Option> dir = new ArrayList<Option>();
		List<Option> fls = new ArrayList<Option>();
		try {
			for (File ff : dirs) {
				if (ff.isDirectory())
					dir.add(new Option(ff.getName(),
							getString(R.string.folder), ff.getAbsolutePath()));
				else {
					fls.add(new Option(ff.getName(),
							getString(R.string.file_size) + ff.length(), ff
									.getAbsolutePath()));
				}
			}
		} catch (Exception e) {

		}
		Collections.sort(dir);
		Collections.sort(fls);
		dir.addAll(fls);
		if (!f.getName().equalsIgnoreCase("sdcard"))
			dir.add(0,
					new Option("..", getString(R.string.parent_dir), f
							.getParent()));
		adapter = new FileArrayAdapter(FileChooser.this, R.layout.file_view,
				dir);
		this.setListAdapter(adapter);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		currentDir = new File("/sdcard/");
		fill(currentDir);
	}

	private void onFileClick(Option o) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		Editor ed = settings.edit();
		ed.putString("key_path", o.getPath());
		ed.commit();
		Toast.makeText(this, getString(R.string.file_toast) + o.getPath(),
				Toast.LENGTH_SHORT);
		finish();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Option o = adapter.getItem(position);
		if (o.getData().equalsIgnoreCase(getString(R.string.folder))
				|| o.getData().equalsIgnoreCase(getString(R.string.parent_dir))) {
			currentDir = new File(o.getPath());
			fill(currentDir);
		} else {
			onFileClick(o);
		}
	}
}