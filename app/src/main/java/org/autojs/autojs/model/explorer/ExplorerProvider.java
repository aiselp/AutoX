package org.autojs.autojs.model.explorer;

import io.reactivex.rxjava3.core.Single;

public interface ExplorerProvider {

    Single<? extends ExplorerPage> getExplorerPage(ExplorerPage parent);
}
