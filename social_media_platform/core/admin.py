from django.contrib import admin
from .models import (
    UserProfile, Connection, Follow, Block,
    Group, CompanyPage, Post, Comment,
    Message, Notification, ReportedContent
)

@admin.register(UserProfile)
class UserProfileAdmin(admin.ModelAdmin):
    list_display = ('user', 'profession', 'company_name', 'location', 'is_private', 'is_active_account', 'created_at')
    search_fields = ('user__username', 'user__email', 'user__first_name', 'user__last_name', 'company_name', 'skills')
    list_filter = ('is_private', 'email_verified', 'mobile_verified', 'is_active_account')

@admin.register(Connection)
class ConnectionAdmin(admin.ModelAdmin):
    list_display = ('sender', 'receiver', 'status', 'created_at')
    list_filter = ('status',)
    search_fields = ('sender__username', 'receiver__username')

@admin.register(Follow)
class FollowAdmin(admin.ModelAdmin):
    list_display = ('follower', 'following', 'created_at')
    search_fields = ('follower__username', 'following__username')

@admin.register(Block)
class BlockAdmin(admin.ModelAdmin):
    list_display = ('blocker', 'blocked', 'created_at')
    search_fields = ('blocker__username', 'blocked__username')

@admin.register(Group)
class GroupAdmin(admin.ModelAdmin):
    list_display = ('name', 'creator', 'is_private', 'created_at')
    search_fields = ('name', 'description')
    prepopulated_fields = {'slug': ('name',)}

@admin.register(CompanyPage)
class CompanyPageAdmin(admin.ModelAdmin):
    list_display = ('name', 'industry', 'owner', 'location', 'created_at')
    search_fields = ('name', 'tagline', 'industry')
    prepopulated_fields = {'slug': ('name',)}

@admin.register(Post)
class PostAdmin(admin.ModelAdmin):
    list_display = ('id', 'author', 'content_snippet', 'visibility', 'group', 'company', 'created_at')
    list_filter = ('visibility', 'created_at')
    search_fields = ('author__username', 'content')

    def content_snippet(self, obj):
        return obj.content[:50]

@admin.register(Comment)
class CommentAdmin(admin.ModelAdmin):
    list_display = ('id', 'author', 'post', 'created_at')
    search_fields = ('author__username', 'content')

@admin.register(Message)
class MessageAdmin(admin.ModelAdmin):
    list_display = ('id', 'sender', 'receiver', 'is_read', 'created_at')
    list_filter = ('is_read',)
    search_fields = ('sender__username', 'receiver__username', 'content')

@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ('id', 'recipient', 'sender', 'notification_type', 'is_read', 'created_at')
    list_filter = ('notification_type', 'is_read')
    search_fields = ('recipient__username', 'text')

@admin.register(ReportedContent)
class ReportedContentAdmin(admin.ModelAdmin):
    list_display = ('id', 'reporter', 'post', 'reported_user', 'reason', 'status', 'created_at')
    list_filter = ('status', 'reason')
    search_fields = ('reporter__username', 'reason', 'details')
