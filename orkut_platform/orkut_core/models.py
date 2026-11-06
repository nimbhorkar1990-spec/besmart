from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone

class UserRole(models.Model):
    ROLE_CHOICES = [
        ('SUPER_ADMIN', 'Super Admin'),
        ('ADMIN', 'Admin'),
        ('MODERATOR', 'Moderator'),
        ('NORMAL_USER', 'Normal User'),
    ]
    name = models.CharField(max_length=50, unique=True)
    code = models.CharField(max_length=20, choices=ROLE_CHOICES, default='NORMAL_USER')
    description = models.TextField(blank=True)
    is_system_role = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.name


class RolePermission(models.Model):
    role = models.OneToOneField(UserRole, on_delete=models.CASCADE, related_name='permissions')
    can_edit_profile = models.BooleanField(default=True)
    can_upload_photos = models.BooleanField(default=True)
    can_create_posts = models.BooleanField(default=True)
    can_manage_posts = models.BooleanField(default=True)
    can_like_comment = models.BooleanField(default=True)
    can_send_messages = models.BooleanField(default=True)
    can_send_friend_requests = models.BooleanField(default=True)
    can_create_communities = models.BooleanField(default=True)
    can_join_communities = models.BooleanField(default=True)
    can_create_albums = models.BooleanField(default=True)
    can_use_scrapbook = models.BooleanField(default=True)
    can_access_social_features = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"Permissions for {self.role.name}"


class UserPermission(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='custom_permissions')
    can_edit_profile = models.BooleanField(default=True)
    can_upload_photos = models.BooleanField(default=True)
    can_create_posts = models.BooleanField(default=True)
    can_manage_posts = models.BooleanField(default=True)
    can_like_comment = models.BooleanField(default=True)
    can_send_messages = models.BooleanField(default=True)
    can_send_friend_requests = models.BooleanField(default=True)
    can_create_communities = models.BooleanField(default=True)
    can_join_communities = models.BooleanField(default=True)
    can_create_albums = models.BooleanField(default=True)
    can_use_scrapbook = models.BooleanField(default=True)
    can_access_social_features = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"Custom Permissions for {self.user.username}"


class UserProfile(models.Model):
    GENDER_CHOICES = [
        ('M', 'Male'),
        ('F', 'Female'),
        ('O', 'Other'),
    ]
    RELATIONSHIP_CHOICES = [
        ('Single', 'Single'),
        ('In a relationship', 'In a relationship'),
        ('Engaged', 'Engaged'),
        ('Married', 'Married'),
        ('It\'s complicated', 'It\'s complicated'),
        ('Open', 'Open'),
    ]
    SCRAPBOOK_PRIVACY_CHOICES = [
        ('EVERYONE', 'Everyone'),
        ('FRIENDS', 'Friends Only'),
        ('ONLY_ME', 'Only Me'),
    ]
    ACCOUNT_STATUS_CHOICES = [
        ('ACTIVE', 'Active'),
        ('INACTIVE', 'Inactive'),
        ('BLOCKED', 'Blocked'),
        ('PENDING_DELETION', 'Pending Deletion'),
        ('DELETED', 'Deleted'),
    ]

    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='orkut_profile')
    role = models.ForeignKey(UserRole, on_delete=models.SET_NULL, null=True, blank=True, related_name='users')
    account_status = models.CharField(max_length=20, choices=ACCOUNT_STATUS_CHOICES, default='ACTIVE')

    full_name = models.CharField(max_length=150, blank=True)
    mobile_number = models.CharField(max_length=20, blank=True)
    gender = models.CharField(max_length=1, choices=GENDER_CHOICES, default='M')
    date_of_birth = models.DateField(null=True, blank=True)
    city = models.CharField(max_length=100, blank=True, default='Mumbai')
    country = models.CharField(max_length=100, blank=True, default='India')
    profile_photo = models.ImageField(upload_to='profiles/', blank=True, null=True)
    cover_photo = models.ImageField(upload_to='covers/', blank=True, null=True)
    
    about_me = models.TextField(blank=True, default="Living life one day at a time!")
    relationship_status = models.CharField(max_length=50, choices=RELATIONSHIP_CHOICES, default='Single')
    interests_hobbies = models.TextField(blank=True, default="Coding, Music, Travel, Reading")
    education = models.CharField(max_length=200, blank=True, default="Bachelor of Science in Computer Science")
    profession = models.CharField(max_length=150, blank=True, default="Software Developer")
    
    # Orkut Special Features
    fortune = models.CharField(max_length=255, blank=True, default="A pleasant surprise is waiting for you today.")
    profile_views = models.PositiveIntegerField(default=0)
    
    # Classic Orkut Badges / Ratings (0-3 scale or percentage)
    trusty_rating = models.PositiveSmallIntegerField(default=3)  # out of 3
    cool_rating = models.PositiveSmallIntegerField(default=3)    # out of 3
    sexy_rating = models.PositiveSmallIntegerField(default=3)    # out of 3
    
    is_online = models.BooleanField(default=False)
    last_seen = models.DateTimeField(default=timezone.now)
    scrapbook_privacy = models.CharField(max_length=20, choices=SCRAPBOOK_PRIVACY_CHOICES, default='FRIENDS')
    is_suspended = models.BooleanField(default=False)
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.user.username} ({self.full_name or self.user.username})"

    def avatar_url(self):
        if self.profile_photo and hasattr(self.profile_photo, 'url'):
            return self.profile_photo.url
        name_encoded = (self.full_name or self.user.username).replace(' ', '+')
        return f"https://ui-avatars.com/api/?name={name_encoded}&background=2b4f81&color=ffffff&bold=true"

    def check_permission(self, perm_name):
        if self.user.is_superuser:
            return True
        if hasattr(self.user, 'custom_permissions'):
            return getattr(self.user.custom_permissions, perm_name, True)
        if self.role and hasattr(self.role, 'permissions'):
            return getattr(self.role.permissions, perm_name, True)
        return True

    def is_super_admin(self):
        return self.user.is_superuser or (self.role and self.role.code == 'SUPER_ADMIN')

    def is_admin_or_super(self):
        return self.user.is_superuser or (self.role and self.role.code in ('SUPER_ADMIN', 'ADMIN'))

    def is_moderator_or_higher(self):
        return self.user.is_superuser or (self.role and self.role.code in ('SUPER_ADMIN', 'ADMIN', 'MODERATOR'))


class AccountDeletionRequest(models.Model):
    STATUS_CHOICES = [
        ('PENDING', 'Pending'),
        ('APPROVED', 'Approved'),
        ('REJECTED', 'Rejected'),
        ('COMPLETED', 'Completed'),
        ('CANCELLED', 'Cancelled'),
    ]
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='deletion_requests')
    reason = models.TextField(blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='PENDING')
    admin_notes = models.TextField(blank=True)
    reviewed_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='reviewed_deletions')
    reviewed_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Deletion Request by {self.user.username} [{self.status}]"


class AdminActivityLog(models.Model):
    admin_user = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, related_name='admin_actions')
    action = models.CharField(max_length=150)
    affected_user = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='affected_actions')
    previous_value = models.TextField(blank=True)
    updated_value = models.TextField(blank=True)
    ip_address = models.CharField(max_length=50, blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.admin_user.username if self.admin_user else 'System'} - {self.action} ({self.created_at.strftime('%Y-%m-%d %H:%M')})"


class FriendRequest(models.Model):
    STATUS_CHOICES = [
        ('PENDING', 'Pending'),
        ('ACCEPTED', 'Accepted'),
        ('REJECTED', 'Rejected'),
    ]
    from_user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_friend_requests')
    to_user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_friend_requests')
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default='PENDING')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('from_user', 'to_user')

    def __str__(self):
        return f"{self.from_user.username} -> {self.to_user.username} [{self.status}]"


class Friendship(models.Model):
    user1 = models.ForeignKey(User, on_delete=models.CASCADE, related_name='friendships1')
    user2 = models.ForeignKey(User, on_delete=models.CASCADE, related_name='friendships2')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('user1', 'user2')

    def __str__(self):
        return f"{self.user1.username} <-> {self.user2.username}"


class StatusPost(models.Model):
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='orkut_posts')
    content = models.TextField()
    image = models.ImageField(upload_to='posts/', blank=True, null=True)
    link_url = models.URLField(max_length=500, blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.author.username}: {self.content[:40]}"


class PostLike(models.Model):
    post = models.ForeignKey(StatusPost, on_delete=models.CASCADE, related_name='likes')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='post_likes')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('post', 'user')


class PostComment(models.Model):
    post = models.ForeignKey(StatusPost, on_delete=models.CASCADE, related_name='comments')
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='post_comments')
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']


class ScrapbookMessage(models.Model):
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_scraps')
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_scraps')
    message = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    is_read = models.BooleanField(default=False)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Scrap from {self.sender.username} to {self.recipient.username}"


class Community(models.Model):
    name = models.CharField(max_length=150, unique=True)
    description = models.TextField()
    category = models.CharField(max_length=100, default='Technology & Internet')
    profile_picture = models.ImageField(upload_to='communities/', blank=True, null=True)
    is_private = models.BooleanField(default=False)
    creator = models.ForeignKey(User, on_delete=models.CASCADE, related_name='created_communities')
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.name

    def avatar_url(self):
        if self.profile_picture and hasattr(self.profile_picture, 'url'):
            return self.profile_picture.url
        name_encoded = self.name.replace(' ', '+')
        return f"https://ui-avatars.com/api/?name={name_encoded}&background=d82b7d&color=ffffff&bold=true"


class CommunityMember(models.Model):
    ROLE_CHOICES = [
        ('OWNER', 'Owner'),
        ('MODERATOR', 'Moderator'),
        ('MEMBER', 'Member'),
    ]
    STATUS_CHOICES = [
        ('ACTIVE', 'Active'),
        ('PENDING', 'Pending Request'),
    ]
    community = models.ForeignKey(Community, on_delete=models.CASCADE, related_name='members')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='community_memberships')
    role = models.CharField(max_length=15, choices=ROLE_CHOICES, default='MEMBER')
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default='ACTIVE')
    joined_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('community', 'user')


class CommunityPost(models.Model):
    community = models.ForeignKey(Community, on_delete=models.CASCADE, related_name='posts')
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='community_posts')
    title = models.CharField(max_length=200)
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']


class CommunityComment(models.Model):
    topic = models.ForeignKey(CommunityPost, on_delete=models.CASCADE, related_name='replies')
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='community_replies')
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']


class PhotoAlbum(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='photo_albums')
    title = models.CharField(max_length=150)
    description = models.TextField(blank=True)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user.username} - {self.title}"


class Photo(models.Model):
    album = models.ForeignKey(PhotoAlbum, on_delete=models.CASCADE, related_name='photos')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='photos')
    image = models.ImageField(upload_to='photos/')
    caption = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']


class PhotoComment(models.Model):
    photo = models.ForeignKey(Photo, on_delete=models.CASCADE, related_name='comments')
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='photo_comments')
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)


class PrivateMessage(models.Model):
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_messages')
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_messages')
    message = models.TextField()
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"{self.sender.username} to {self.recipient.username}: {self.message[:30]}"


class ProfileVisitor(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='visitors')
    visitor = models.ForeignKey(User, on_delete=models.CASCADE, related_name='visited_profiles')
    visited_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('user', 'visitor')
        ordering = ['-visited_at']


class Fan(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='fans')
    fan = models.ForeignKey(User, on_delete=models.CASCADE, related_name='idols')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('user', 'fan')


class Notification(models.Model):
    TYPE_CHOICES = [
        ('FRIEND_REQ', 'Friend Request'),
        ('FRIEND_ACC', 'Friend Request Accepted'),
        ('SCRAP', 'New Scrap Message'),
        ('MESSAGE', 'New Private Message'),
        ('LIKE', 'Liked your post'),
        ('COMMENT', 'Commented on your post'),
        ('FAN', 'Added you as a Fan'),
        ('COMMUNITY', 'Community Activity'),
        ('DELETION', 'Account Deletion Status Update'),
        ('SYSTEM', 'System Administration Notice'),
    ]
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='orkut_notifications')
    sender = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True)
    notification_type = models.CharField(max_length=15, choices=TYPE_CHOICES)
    title = models.CharField(max_length=200)
    message = models.TextField()
    link = models.CharField(max_length=255, default='/')
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']


class Report(models.Model):
    STATUS_CHOICES = [
        ('PENDING', 'Pending'),
        ('RESOLVED', 'Resolved'),
        ('DISMISSED', 'Dismissed'),
    ]
    reported_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name='reports_made')
    content_type = models.CharField(max_length=50) # 'post', 'comment', 'scrap', 'community', 'user', 'photo'
    content_id = models.PositiveIntegerField()
    reason = models.TextField()
    status = models.CharField(max_length=15, choices=STATUS_CHOICES, default='PENDING')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']
