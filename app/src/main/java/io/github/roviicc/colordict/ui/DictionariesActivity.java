package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.data.InstalledDict;

/**
 * Manage installed dictionaries: enable/disable, reorder, recolor, import
 * new ones through the system file picker, or delete.
 */
public class DictionariesActivity extends BaseActivity implements DictRepository.Listener {

    private static final int REQ_IMPORT_FILES = 1;
    private static final int REQ_IMPORT_TREE = 2;

    private DictAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionaries);
        setTitle(R.string.dictionaries);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ListView list = findViewById(R.id.dictList);
        adapter = new DictAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.dictEmpty));
        registerForContextMenu(list);
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo().addListener(this);
        adapter.reload();
    }

    @Override
    protected void onStop() {
        repo().removeListener(this);
        super.onStop();
    }

    @Override
    public void onDictionariesChanged() {
        adapter.reload();
    }

    // ------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dictionaries, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_import_files) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(pick, REQ_IMPORT_FILES);
        } else if (id == R.id.action_import_folder) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    REQ_IMPORT_TREE);
        } else if (id == R.id.action_rescan) {
            repo().rescan();
            Toast.makeText(this, R.string.rescanning, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_add_help) {
            showAddHelp();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQ_IMPORT_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                DictImporter.importFiles(this, repo(), uris);
            }
        } else if (requestCode == REQ_IMPORT_TREE && data.getData() != null) {
            DictImporter.importTree(this, repo(), data.getData());
        }
    }

    private void showAddHelp() {
        File externalHint = repo().externalDictDir();
        String path = externalHint != null
                ? externalHint.getAbsolutePath() : repo().internalDictDir().getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_add_help)
                .setMessage(getString(R.string.add_help_text, path))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------ context menu

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, v, info);
        if (v.getId() == R.id.dictList) {
            getMenuInflater().inflate(R.menu.menu_dict_item, menu);
            AdapterView.AdapterContextMenuInfo cmi = (AdapterView.AdapterContextMenuInfo) info;
            menu.setHeaderTitle(adapter.getItem(cmi.position).name());
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) {
            return super.onContextItemSelected(item);
        }
        InstalledDict dict = adapter.getItem(info.position);
        int id = item.getItemId();
        if (id == R.id.action_move_up) {
            repo().move(dict, -1);
        } else if (id == R.id.action_move_down) {
            repo().move(dict, 1);
        } else if (id == R.id.action_details) {
            showDetails(dict);
        } else if (id == R.id.action_delete) {
            confirmDelete(dict);
        } else {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    private void showDetails(InstalledDict dict) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.detail_words, dict.wordCount())).append('\n');
        sb.append(getString(R.string.detail_path, dict.ifoFile.getAbsolutePath())).append('\n');
        if (!dict.info.author.isEmpty()) {
            sb.append(getString(R.string.detail_author, dict.info.author)).append('\n');
        }
        if (!dict.info.description.isEmpty()) {
            sb.append('\n').append(dict.info.description.replace("<br>", "\n"));
        }
        if (dict.loadError != null) {
            sb.append('\n').append(getString(R.string.detail_error, dict.loadError));
        }
        new AlertDialog.Builder(this)
                .setTitle(dict.name())
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmDelete(InstalledDict dict) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete)
                .setMessage(getString(R.string.delete_confirm, dict.name()))
                .setPositiveButton(R.string.action_delete,
                        (d, w) -> repo().delete(dict))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ adapter

    private final class DictAdapter extends BaseAdapter {

        private List<InstalledDict> items = new ArrayList<>();

        void reload() {
            items = repo().dictionaries();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public InstalledDict getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(DictionariesActivity.this)
                            .inflate(R.layout.item_dictionary, parent, false);
            InstalledDict dict = getItem(position);

            View chip = view.findViewById(R.id.dictColorChip);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(dict.color);
            chip.setBackground(bg);
            chip.setOnClickListener(v -> ColorPickerDialog.show(
                    DictionariesActivity.this, dict.color,
                    color -> repo().setColor(dict, color)));

            TextView name = view.findViewById(R.id.dictName);
            name.setText(dict.name());

            TextView sub = view.findViewById(R.id.dictSubtitle);
            String subtitle = getString(R.string.dict_subtitle,
                    dict.wordCount(), dict.location);
            if (dict.loadError != null) {
                subtitle += " — " + getString(R.string.load_failed);
            }
            sub.setText(subtitle);

            Switch toggle = view.findViewById(R.id.dictEnabled);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(dict.enabled);
            toggle.setOnCheckedChangeListener(
                    (button, checked) -> repo().setEnabled(dict, checked));
            return view;
        }
    }
}
