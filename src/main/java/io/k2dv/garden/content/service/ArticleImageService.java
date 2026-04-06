package io.k2dv.garden.content.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.content.dto.ArticleImagePositionItem;
import io.k2dv.garden.content.dto.ArticleImageResponse;
import io.k2dv.garden.content.dto.CreateArticleImageRequest;
import io.k2dv.garden.content.model.Article;
import io.k2dv.garden.content.model.ArticleImage;
import io.k2dv.garden.content.repository.ArticleImageRepository;
import io.k2dv.garden.content.repository.ArticleRepository;
import io.k2dv.garden.content.repository.BlogRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleImageService {

    private final ArticleImageRepository imageRepo;
    private final ArticleRepository articleRepo;
    private final BlogRepository blogRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional
    public ArticleImageResponse addImage(UUID blogId, UUID articleId, CreateArticleImageRequest req) {
        verifyBlogExists(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));

        int nextPosition = imageRepo.countByArticleId(articleId) + 1;
        ArticleImage img = new ArticleImage();
        img.setArticleId(articleId);
        img.setBlobId(req.blobId());
        img.setAltText(req.altText());
        img.setPosition(nextPosition);
        img = imageRepo.save(img);

        if (article.getFeaturedImageId() == null) {
            article.setFeaturedImageId(img.getId());
        }

        return toResponse(img);
    }

    @Transactional
    public void deleteImage(UUID blogId, UUID articleId, UUID imageId) {
        verifyBlogExists(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        ArticleImage img = imageRepo.findById(imageId)
            .filter(i -> i.getArticleId().equals(articleId))
            .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));

        boolean wasFeatured = imageId.equals(article.getFeaturedImageId());
        imageRepo.delete(img);

        if (wasFeatured) {
            List<ArticleImage> remaining = imageRepo.findByArticleIdOrderByPositionAsc(articleId);
            article.setFeaturedImageId(remaining.isEmpty() ? null : remaining.get(0).getId());
        }
    }

    @Transactional
    public void reorderImages(UUID blogId, UUID articleId, List<ArticleImagePositionItem> items) {
        verifyBlogExists(blogId);
        articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        for (ArticleImagePositionItem item : items) {
            imageRepo.findById(item.id())
                .filter(i -> i.getArticleId().equals(articleId))
                .ifPresent(i -> i.setPosition(item.position()));
        }
    }

    private void verifyBlogExists(UUID blogId) {
        if (!blogRepo.existsById(blogId)) {
            throw new NotFoundException("BLOG_NOT_FOUND", "Blog not found");
        }
    }

    private ArticleImageResponse toResponse(ArticleImage img) {
        String url = blobRepo.findById(img.getBlobId())
            .map(b -> storageService.resolveUrl(b.getKey()))
            .orElse("");
        return new ArticleImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
    }
}
