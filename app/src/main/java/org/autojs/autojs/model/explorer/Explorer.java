package org.autojs.autojs.model.explorer;

import static org.autojs.autojs.model.explorer.ExplorerChangeEvent.CHANGE;
import static org.autojs.autojs.model.explorer.ExplorerChangeEvent.CHILDREN_CHANGE;
import static org.autojs.autojs.model.explorer.ExplorerChangeEvent.CREATE;
import static org.autojs.autojs.model.explorer.ExplorerChangeEvent.REMOVE;

import androidx.annotation.Nullable;
import android.util.LruCache;

import com.stardust.pio.PFile;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.subjects.PublishSubject;



public class Explorer {
    public static final PublishSubject<ExplorerChangeEvent> EVENTS = PublishSubject.create();

    private final ExplorerProvider mExplorerProvider;
    @Nullable
    private final LruCache<String, ExplorerPage> mExplorerPageLruCache;

    public Explorer(ExplorerProvider explorerProvider, int cacheSize) {
        mExplorerPageLruCache = cacheSize <= 0 ? null : new LruCache<>(cacheSize);
        mExplorerProvider = explorerProvider;
    }

    public ExplorerProvider getProvider() {
        return mExplorerProvider;
    }

    public void notifyChildrenChanged(ExplorerPage page) {
        clearCache(page);
        EVENTS.onNext(new ExplorerChangeEvent(page, CHILDREN_CHANGE, null));
    }

    public void notifyItemChanged(ExplorerItem oldItem, ExplorerItem newItem) {
        ExplorerPage parent = getParent(oldItem);
        ExplorerPage cachedParent = getFromCache(parent);
        if (cachedParent != null) {
            cachedParent.updateChild(oldItem, newItem);
        }
        EVENTS.onNext(new ExplorerChangeEvent(parent, CHANGE, oldItem, newItem));
    }

    private ExplorerPage getFromCache(ExplorerPage parent) {
        if(mExplorerPageLruCache == null || parent == null){
            return null;
        }
        return mExplorerPageLruCache.get(parent.getPath());
    }

    private ExplorerPage getParentFromCache(ExplorerItem item) {
        return getFromCache(getParent(item));
    }


    public void notifyItemRemoved(ExplorerItem item) {
        ExplorerPage parent = getParent(item);
        ExplorerPage cachedParent = getFromCache(parent);
        if (cachedParent != null) {
            cachedParent.removeChild(item);
        }
        EVENTS.onNext(new ExplorerChangeEvent(parent, REMOVE, item));
    }

    private ExplorerPage getParent(ExplorerItem item) {
        ExplorerPage parent = item.getParent();
        if (parent == null) {
            if (item instanceof ExplorerFileItem) {
                PFile parentFile = ((ExplorerFileItem) item).getFile().getParentFile();
                return new ExplorerDirPage(parentFile, null);
            }
            return null;
        }
        return parent;
    }

    public void notifyItemCreated(ExplorerItem item) {
        ExplorerPage parent = getParent(item);
        ExplorerPage cachedParent = getFromCache(parent);
        if (cachedParent != null) {
            cachedParent.addChild(item);
        }
        EVENTS.onNext(new ExplorerChangeEvent(parent, CREATE, item, item));
    }

    public void refreshAll() {
        if (mExplorerPageLruCache != null)
            mExplorerPageLruCache.evictAll();
        EVENTS.onNext(ExplorerChangeEvent.EVENT_ALL);
    }


    public Single<ExplorerPage> fetchChildren(ExplorerPage page) {
        ExplorerPage cachedGroup = mExplorerPageLruCache == null ? null : mExplorerPageLruCache.get(page.getPath());
        if (cachedGroup != null) {
            page.copyChildren(cachedGroup);
            return Single.just(page);
        }
        return mExplorerProvider.getExplorerPage(page)
                .observeOn(AndroidSchedulers.mainThread())
                .map(g -> {
                    if (mExplorerPageLruCache != null)
                        mExplorerPageLruCache.put(g.getPath(), g);
                    page.copyChildren(g);
                    return page;
                });
    }

    private void clearCache(ExplorerPage item) {
        if (mExplorerPageLruCache != null)
            mExplorerPageLruCache.remove(item.getPath());
    }

    public @NonNull Disposable registerChangeListener(Consumer<ExplorerChangeEvent> subscriber) {
        return EVENTS.observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread()).subscribe(subscriber);
    }

}
