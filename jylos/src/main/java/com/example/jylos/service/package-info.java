/**
 * Application/business services.
 *
 * <p>This package intentionally mixes a few service sub-roles under one layer:</p>
 * <ul>
 *   <li>entity/application services such as note, folder and tag workflows</li>
 *   <li>feature services such as backlinks, import and rich-link fetching</li>
 *   <li>technical services such as encryption, backups and note history</li>
 *   <li>warm indexes/caches such as {@code NoteTitleIndex}</li>
 * </ul>
 *
 * <p>Classes here must stay independent from JavaFX presentation types and must not
 * reach into {@code ui/*}. Prefer explicit dependencies over opportunistic global
 * lookups or singleton event-bus access.</p>
 */
package com.example.jylos.service;
