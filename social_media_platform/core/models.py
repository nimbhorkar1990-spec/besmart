from django.db import models
from django.contrib.auth.models import User
from django.utils.text import slugify
from django.db.models.signals import post_save
from django.dispatch import receiver
from django.db.models import Q


class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='profile')
    headline = models.CharField(max_length=255, blank=True, default='')
    bio = models.TextField(blank=True, default='')
    location = models.CharField(max_length=150, blank=True, default='')
    education = models.CharField(max_length=200, blank=True, default='')
    profession = models.CharField(max_length=150, blank=True, default='')
    company_name = models.CharField(max_length=150, blank=True, default='')
    skills = models.TextField(blank=True, default='', help_text="Comma-separated skills, e.g. Python, Django, React")
    interests = models.TextField(blank=True, default='', help_text="Interests & hobbies")
    mobile_number = models.CharField(max_length=25, blank=True, default='')
    whatsapp_number = models.CharField(max_length=25, blank=True, default='')
    contact_email = models.EmailField(blank=True, default='')
    linkedin_url = models.URLField(blank=True, default='')
    github_url = models.URLField(blank=True, default='')
    twitter_url = models.URLField(blank=True, default='')
    website_url = models.URLField(blank=True, default='')
    avatar = models.ImageField(upload_to='avatars/', null=True, blank=True)
    cover_photo = models.ImageField(upload_to='covers/', null=True, blank=True)
    avatar_url = models.CharField(max_length=500, blank=True, default='')
    is_private = models.BooleanField(default=False)
    email_verified = models.BooleanField(default=True)
    mobile_verified = models.BooleanField(default=True)
    is_active_account = models.BooleanField(default=True)
    is_online = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.user.username}'s Profile"

    def get_avatar(self):
        if self.avatar:
            return self.avatar.url
        if self.avatar_url:
            return self.avatar_url
        name = self.user.get_full_name() or self.user.username
        return f"https://ui-avatars.com/api/?name={name}&background=1a73e8&color=fff&size=128"

    def get_cover(self):
        if self.cover_photo:
            return self.cover_photo.url
        return "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80"

    def get_skills_list(self):
        if not self.skills:
            return []
        return [s.strip() for s in self.skills.split(',') if s.strip()]

    def connections_count(self):
        return Connection.objects.filter(
            (Q(sender=self.user) | Q(receiver=self.user)),
            status='accepted'
        ).count()

    def followers_count(self):
        return Follow.objects.filter(following=self.user).count()

    def following_count(self):
        return Follow.objects.filter(follower=self.user).count()

    def posts_count(self):
        return self.user.posts.count()

    def is_connected_with(self, other_user):
        if not other_user or not other_user.is_authenticated:
            return False
        return Connection.objects.filter(
            (Q(sender=self.user, receiver=other_user) | Q(sender=other_user, receiver=self.user)),
            status='accepted'
        ).exists()

    def has_pending_request_from(self, other_user):
        return Connection.objects.filter(sender=other_user, receiver=self.user, status='pending').exists()

    def has_sent_request_to(self, other_user):
        return Connection.objects.filter(sender=self.user, receiver=other_user, status='pending').exists()

    def is_following(self, other_user):
        if not other_user or not other_user.is_authenticated:
            return False
        return Follow.objects.filter(follower=self.user, following=other_user).exists()

    def is_blocked_by_or_blocking(self, other_user):
        return Block.objects.filter(
            (Q(blocker=self.user, blocked=other_user) | Q(blocker=other_user, blocked=self.user))
        ).exists()

    def mutual_connections(self, other_user):
        if not other_user or other_user == self.user:
            return []
        user_conns = set(
            Connection.objects.filter(
                (Q(sender=self.user) | Q(receiver=self.user)), status='accepted'
            ).values_list('sender_id', 'receiver_id')
        )
        my_ids = set()
        for s, r in user_conns:
            my_ids.add(r if s == self.user.id else s)

        other_conns = set(
            Connection.objects.filter(
                (Q(sender=other_user) | Q(receiver=other_user)), status='accepted'
            ).values_list('sender_id', 'receiver_id')
        )
        other_ids = set()
        for s, r in other_conns:
            other_ids.add(r if s == other_user.id else s)

        mutual_ids = my_ids.intersection(other_ids)
        return User.objects.filter(id__in=mutual_ids).select_related('profile')


@receiver(post_save, sender=User)
def create_or_update_user_profile(sender, instance, created, **kwargs):
    if created:
        UserProfile.objects.create(
            user=instance,
            contact_email=instance.email,
            headline='SmartSearch Network Member',
            bio=f'Hello! I am {instance.get_full_name() or instance.username} on SmartSearch Social Media Platform.'
        )
    else:
        if hasattr(instance, 'profile'):
            instance.profile.save()


class Connection(models.Model):
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('accepted', 'Accepted'),
        ('rejected', 'Rejected'),
    ]
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_connection_requests')
    receiver = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_connection_requests')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('sender', 'receiver')
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.sender.username} -> {self.receiver.username} ({self.status})"


class Follow(models.Model):
    follower = models.ForeignKey(User, on_delete=models.CASCADE, related_name='following_relations')
    following = models.ForeignKey(User, on_delete=models.CASCADE, related_name='follower_relations')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('follower', 'following')
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.follower.username} follows {self.following.username}"


class Block(models.Model):
    blocker = models.ForeignKey(User, on_delete=models.CASCADE, related_name='blocked_users')
    blocked = models.ForeignKey(User, on_delete=models.CASCADE, related_name='blocked_by_users')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('blocker', 'blocked')

    def __str__(self):
        return f"{self.blocker.username} blocked {self.blocked.username}"


class Group(models.Model):
    name = models.CharField(max_length=150)
    slug = models.SlugField(max_length=160, unique=True, blank=True)
    description = models.TextField(blank=True, default='')
    cover_image = models.ImageField(upload_to='group_covers/', null=True, blank=True)
    creator = models.ForeignKey(User, on_delete=models.CASCADE, related_name='created_groups')
    members = models.ManyToManyField(User, related_name='joined_groups', blank=True)
    is_private = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return self.name

    def save(self, *args, **kwargs):
        if not self.slug:
            self.slug = slugify(self.name)
            base_slug = self.slug
            counter = 1
            while Group.objects.filter(slug=self.slug).exists():
                self.slug = f"{base_slug}-{counter}"
                counter += 1
        super().save(*args, **kwargs)

    def get_cover(self):
        if self.cover_image:
            return self.cover_image.url
        return "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1200&q=80"


class CompanyPage(models.Model):
    name = models.CharField(max_length=150)
    slug = models.SlugField(max_length=160, unique=True, blank=True)
    tagline = models.CharField(max_length=255, blank=True, default='')
    description = models.TextField(blank=True, default='')
    industry = models.CharField(max_length=100, default='Technology & Software')
    company_size = models.CharField(max_length=50, default='50-200 employees')
    website = models.URLField(blank=True, default='')
    location = models.CharField(max_length=150, blank=True, default='')
    logo = models.ImageField(upload_to='company_logos/', null=True, blank=True)
    cover_image = models.ImageField(upload_to='company_covers/', null=True, blank=True)
    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name='owned_companies')
    followers = models.ManyToManyField(User, related_name='followed_companies', blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return self.name

    def save(self, *args, **kwargs):
        if not self.slug:
            self.slug = slugify(self.name)
            base_slug = self.slug
            counter = 1
            while CompanyPage.objects.filter(slug=self.slug).exists():
                self.slug = f"{base_slug}-{counter}"
                counter += 1
        super().save(*args, **kwargs)

    def get_logo(self):
        if self.logo:
            return self.logo.url
        return f"https://ui-avatars.com/api/?name={self.name}&background=0d6efd&color=fff&size=128"

    def get_cover(self):
        if self.cover_image:
            return self.cover_image.url
        return "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=1200&q=80"


class Post(models.Model):
    VISIBILITY_CHOICES = [
        ('public', 'Public'),
        ('connections', 'Connections Only'),
        ('only_me', 'Only Me'),
    ]
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='posts')
    content = models.TextField()
    image = models.ImageField(upload_to='post_images/', null=True, blank=True)
    video = models.FileField(upload_to='post_videos/', null=True, blank=True)
    link_url = models.URLField(blank=True, default='')
    group = models.ForeignKey(Group, on_delete=models.CASCADE, null=True, blank=True, related_name='posts')
    company = models.ForeignKey(CompanyPage, on_delete=models.CASCADE, null=True, blank=True, related_name='posts')
    visibility = models.CharField(max_length=20, choices=VISIBILITY_CHOICES, default='public')
    likes = models.ManyToManyField(User, related_name='liked_posts', blank=True)
    saves = models.ManyToManyField(User, related_name='saved_posts', blank=True)
    shares_count = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.author.username}: {self.content[:40]}"

    def likes_count(self):
        return self.likes.count()

    def comments_count(self):
        return self.comments.count()

    def is_liked_by(self, user):
        if not user or not user.is_authenticated:
            return False
        return self.likes.filter(id=user.id).exists()

    def is_saved_by(self, user):
        if not user or not user.is_authenticated:
            return False
        return self.saves.filter(id=user.id).exists()


class Comment(models.Model):
    post = models.ForeignKey(Post, on_delete=models.CASCADE, related_name='comments')
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name='comments')
    parent = models.ForeignKey('self', on_delete=models.CASCADE, null=True, blank=True, related_name='replies')
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"{self.author.username} on post #{self.post.id}"


class Message(models.Model):
    sender = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sent_messages')
    receiver = models.ForeignKey(User, on_delete=models.CASCADE, related_name='received_messages')
    content = models.TextField(blank=True, default='')
    attachment = models.FileField(upload_to='chat_attachments/', null=True, blank=True)
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f"{self.sender.username} -> {self.receiver.username}: {self.content[:30]}"


class Notification(models.Model):
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name='notifications')
    sender = models.ForeignKey(User, on_delete=models.CASCADE, null=True, blank=True, related_name='triggered_notifications')
    notification_type = models.CharField(max_length=40)
    post = models.ForeignKey(Post, on_delete=models.CASCADE, null=True, blank=True)
    text = models.CharField(max_length=255)
    link = models.CharField(max_length=255, blank=True, default='')
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Notification for {self.recipient.username}: {self.text}"


class ReportedContent(models.Model):
    STATUS_CHOICES = [
        ('pending', 'Pending Review'),
        ('resolved', 'Resolved'),
        ('dismissed', 'Dismissed'),
    ]
    reporter = models.ForeignKey(User, on_delete=models.CASCADE, related_name='reports_submitted')
    post = models.ForeignKey(Post, on_delete=models.CASCADE, null=True, blank=True, related_name='reports')
    reported_user = models.ForeignKey(User, on_delete=models.CASCADE, null=True, blank=True, related_name='reports_against')
    reason = models.CharField(max_length=100)
    details = models.TextField(blank=True, default='')
    status = models.CharField(max_length=20, default='pending', choices=STATUS_CHOICES)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"Report by {self.reporter.username}: {self.reason}"
