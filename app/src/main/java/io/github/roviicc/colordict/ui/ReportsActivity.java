package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.ReportLog;

/**
 * The words this reader reported as missing a connotation note.
 *
 * <p>Three things this screen exists to make true: you can see exactly what
 * would be sent, you can take anything out of it first, and sending is a thing
 * you do rather than a thing that happens. The log never leaves the device on
 * its own - there is no network code in this feature at all.
 */
public class ReportsActivity extends BaseActivity {

    private ReportLog log;
    private ArrayAdapter<ReportLog.Report> adapter;
    private final List<ReportLog.Report> reports = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        log = new ReportLog(this);

        ListView list = findViewById(R.id.reportsList);
        adapter = new ArrayAdapter<ReportLog.Report>(this, R.layout.item_report_row,
                R.id.rowLemma, reports) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                ReportLog.Report r = getItem(position);
                TextView lemma = row.findViewById(R.id.rowLemma);
                TextView gloss = row.findViewById(R.id.rowGloss);
                if (r != null) {
                    lemma.setText(r.lemma);
                    // A not-found report has no sense at all, so say which kind
                    // it is - the two are different problems and the list should
                    // not blur them together. Otherwise show the sense id, which
                    // is what distinguishes one of *break*'s 75 senses from the
                    // rest; the gloss is not in the link, because shipping
                    // 163,494 copies of it cost 6.3 MB.
                    if (ReportLog.REASON_NOT_FOUND.equals(r.reason)) {
                        gloss.setText(R.string.report_not_found);
                    } else {
                        gloss.setText(r.gloss == null || r.gloss.isEmpty()
                                ? r.sense : r.gloss);
                    }
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.reportsEmpty));

        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmRemove(position);
            return true;
        });
        reload();
    }

    private void reload() {
        reports.clear();
        reports.addAll(log.all());
        adapter.notifyDataSetChanged();
        setTitle(reports.isEmpty()
                ? getString(R.string.reported_words)
                : getString(R.string.reported_words_n, reports.size()));
        invalidateOptionsMenu();
    }

    private void confirmRemove(int position) {
        if (position < 0 || position >= reports.size()) {
            return;
        }
        String lemma = reports.get(position).lemma;
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_report_q, lemma))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    log.removeAt(position);
                    reload();
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.send_reports).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, R.string.clear_reports);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean any = !reports.isEmpty();
        if (menu.findItem(1) != null) {
            menu.findItem(1).setEnabled(any);
        }
        if (menu.findItem(2) != null) {
            menu.findItem(2).setEnabled(any);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            share();
            return true;
        }
        if (item.getItemId() == 2) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.clear_reports_q)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.clear, (d, w) -> {
                        log.clear();
                        reload();
                    })
                    .show();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Hands a copy of the log to the share sheet. Nothing is sent by us. */
    private void share() {
        Uri uri = log.exportForSharing();
        if (uri == null) {
            Toast.makeText(this, R.string.no_reports, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.reported_words))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.send_reports)));
    }
}
