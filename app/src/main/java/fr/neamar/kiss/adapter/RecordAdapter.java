package fr.neamar.kiss.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.normalizer.StringNormalizer;
import fr.neamar.kiss.result.AppResult;
import fr.neamar.kiss.result.ContactsResult;
import fr.neamar.kiss.result.PhoneResult;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.result.SearchResult;
import fr.neamar.kiss.result.SettingsResult;
import fr.neamar.kiss.result.ShortcutsResult;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.utils.FuzzyScore;

public class RecordAdapter extends BaseAdapter {
    private FuzzyScore fuzzyScore;

    /**
     * Array list containing all the results currently displayed
     */
    private List<Result> results;

    public RecordAdapter(QueryInterface parent, ArrayList<Result> results) {
        this.results = results;
        this.fuzzyScore = null;
    }

    @Override
    public int getViewTypeCount() {
        return 6;
    }

    @Override
    public int getItemViewType(int position) {
        if (results.get(position) instanceof AppResult)
            return 0;
        else if (results.get(position) instanceof SearchResult)
            return 1;
        else if (results.get(position) instanceof ContactsResult)
            return 2;
        else if (results.get(position) instanceof SettingsResult)
            return 3;
        else if (results.get(position) instanceof PhoneResult)
            return 4;
        else if (results.get(position) instanceof ShortcutsResult)
            return 5;
        else
            return -1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getCount() {
        return results.size();
    }

    @Override
    public Object getItem(int position) {
        return results.get(position);
    }

    @Override
    public long getItemId(int position) {
        return results.get(position).getUniqueId();
    }

    @Override
    public @NonNull
    View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = results.get(position).display(parent.getContext(), results.size() - position, convertView, parent, fuzzyScore);
        view.setTag(getItemViewType(position));
        return view;
    }

    public void onClick(final int position, View v) {

    }

    public void removeResult(Context context, Result result) {
    }

    public void updateResults(List<Result> results, String query) {
        this.results.clear();
        this.results.addAll(results);
        StringNormalizer.Result queryNormalized = StringNormalizer.normalizeWithResult(query, false);

        fuzzyScore = new FuzzyScore(queryNormalized.codePoints, true);
        notifyDataSetChanged();
    }

    public void clear() {
    }
}
