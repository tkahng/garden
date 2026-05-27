/**
 * Service layer for the search module, responsible for dispatching full-text queries
 * to the appropriate repositories and assembling the composite {@code SearchResponse}.
 * Each content-type bucket is fetched only when explicitly requested by the caller.
 */
package io.k2dv.garden.search.service;
