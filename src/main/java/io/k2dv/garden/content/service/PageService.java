package io.k2dv.garden.content.service;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.model.SitePage;
import io.k2dv.garden.content.repository.PageRepository;
import io.k2dv.garden.content.specification.PageSpecification;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepo;

    @Transactional
    public AdminPageResponse create(CreatePageRequest req) {
        String handle = req.handle() != null ? req.handle() : slugify(req.title(), "page");
        if (pageRepo.existsByHandleAndDeletedAtIsNull(handle)) {
            throw new ConflictException("HANDLE_CONFLICT", "A page with this handle already exists");
        }
        SitePage page = new SitePage();
        page.setTitle(req.title());
        page.setHandle(handle);
        page.setBody(req.body());
        page.setMetaTitle(req.metaTitle());
        page.setMetaDescription(req.metaDescription());
        page.setStatus(PageStatus.DRAFT);
        return toAdminResponse(pageRepo.save(page));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminPageResponse> list(PageFilterRequest filter, Pageable pageable) {
        var spec = PageSpecification.toSpec(filter);
        Page<SitePage> pages = pageRepo.findAll(spec, pageable);
        return PagedResult.of(pages, this::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse get(UUID id) {
        return toAdminResponse(findOrThrow(id));
    }

    @Transactional
    public AdminPageResponse update(UUID id, UpdatePageRequest req) {
        SitePage page = findOrThrow(id);
        if (req.title() != null) page.setTitle(req.title());
        if (req.handle() != null) {
            if (pageRepo.existsByHandleAndDeletedAtIsNullAndIdNot(req.handle(), id)) {
                throw new ConflictException("HANDLE_CONFLICT", "A page with this handle already exists");
            }
            page.setHandle(req.handle());
        }
        if (req.body() != null) page.setBody(req.body());
        if (req.metaTitle() != null) page.setMetaTitle(req.metaTitle());
        if (req.metaDescription() != null) page.setMetaDescription(req.metaDescription());
        return toAdminResponse(page);
    }

    @Transactional
    public AdminPageResponse changeStatus(UUID id, PageStatusRequest req) {
        SitePage page = findOrThrow(id);
        page.setStatus(req.status());
        if (req.status() == PageStatus.PUBLISHED) {
            page.setPublishedAt(Instant.now());
        } else {
            page.setPublishedAt(null);
        }
        return toAdminResponse(page);
    }

    @Transactional
    public void delete(UUID id) {
        SitePage page = findOrThrow(id);
        page.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse getByHandle(String handle) {
        var pages = pageRepo.findAll(
            PageSpecification.publishedSpec()
                .and((root, q, cb) -> cb.equal(root.get("handle"), handle))
        );
        if (pages.isEmpty()) throw new NotFoundException("PAGE_NOT_FOUND", "Page not found");
        return toResponse(pages.get(0));
    }

    @Transactional(readOnly = true)
    public PagedResult<PageResponse> listPublished(PageFilterRequest filter, Pageable pageable) {
        var spec = PageSpecification.publishedSpec();
        if (filter != null && filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + filter.q().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("body")),  pattern)
            ));
        }
        Page<SitePage> pages = pageRepo.findAll(spec, pageable);
        return PagedResult.of(pages, this::toResponse);
    }

    // --- helpers ---

    private SitePage findOrThrow(UUID id) {
        return pageRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PAGE_NOT_FOUND", "Page not found"));
    }

    static String slugify(String value, String fallback) {
        if (value == null) return fallback;
        String slug = value.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? fallback : slug;
    }

    private AdminPageResponse toAdminResponse(SitePage p) {
        return new AdminPageResponse(p.getId(), p.getTitle(), p.getHandle(), p.getBody(),
            p.getStatus(), p.getMetaTitle(), p.getMetaDescription(),
            p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt());
    }

    private PageResponse toResponse(SitePage p) {
        return new PageResponse(p.getId(), p.getTitle(), p.getHandle(), p.getBody(),
            p.getMetaTitle(), p.getMetaDescription(), p.getPublishedAt());
    }
}
