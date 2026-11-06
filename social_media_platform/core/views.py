from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from django.contrib.auth.decorators import login_required, user_passes_test
from django.contrib import messages
from django.http import JsonResponse, HttpResponseForbidden
from django.db.models import Q, Count
from django.views.decorators.http import require_POST
from django.core.paginator import Paginator
from django.utils import timezone
import json

from .models import (
    UserProfile, Connection, Follow, Block,
    Group, CompanyPage, Post, Comment,
    Message, Notification, ReportedContent,
    UserRole, RolePermission, UserPermission,
    AccountDeletionRequest, AdminActivityLog
)
from .forms import (
    UserRegistrationForm, UserProfileForm, PostForm,
    CommentForm, GroupForm, CompanyPageForm, MessageForm, ReportForm,
    AccountDeletionRequestForm, UserRoleForm, RolePermissionForm,
    UserPermissionForm, AdminUserEditForm, PERM_FIELDS
)


# Helper: Activity & Audit Logging
def log_admin_action(request, action, module_name="General", affected_user=None, prev_val="", new_val=""):
    ip = request.META.get('REMOTE_ADDR', '') if request else ''
    admin_u = request.user if (request and request.user.is_authenticated) else None
    AdminActivityLog.objects.create(
        admin_user=admin_u,
        admin_id=admin_u.id if admin_u else None,
        admin_username=admin_u.username if admin_u else 'System',
        action=action,
        module_name=module_name,
        affected_user_id=affected_user.id if affected_user else None,
        affected_username=affected_user.username if affected_user else '',
        previous_value=str(prev_val)[:500],
        updated_value=str(new_val)[:500],
        ip_address=ip
    )


# Helper: send notification
def create_notification(recipient, sender, notification_type, text, link='', post=None):
    if recipient == sender:
        return None
    return Notification.objects.create(
        recipient=recipient,
        sender=sender,
        notification_type=notification_type,
        text=text,
        link=link,
        post=post
    )


# -------------------------------------------------------------
# 1. Authentication Views
# -------------------------------------------------------------
def register_view(request):
    if request.user.is_authenticated:
        return redirect('feed')
    if request.method == 'POST':
        form = UserRegistrationForm(request.POST)
        if form.is_valid():
            user = form.save(commit=False)
            user.set_password(form.cleaned_data['password'])
            user.first_name = form.cleaned_data['first_name']
            user.last_name = form.cleaned_data['last_name']
            user.save()

            # Profile already created by post_save signal
            profile = user.profile
            profile.mobile_number = form.cleaned_data['mobile_number']
            profile.save()

            messages.success(request, "Registration successful! Welcome to the SmartSearch Social Media Platform.")
            login(request, user)
            return redirect('feed')
    else:
        form = UserRegistrationForm()
    return render(request, 'auth/register.html', {'form': form})


def login_view(request):
    if request.user.is_authenticated:
        return redirect('feed')
    if request.method == 'POST':
        username_or_email = request.POST.get('username', '').strip()
        password = request.POST.get('password', '')

        # Support login by username or email
        user_obj = None
        if '@' in username_or_email:
            try:
                user_match = User.objects.get(email__iexact=username_or_email)
                user_obj = authenticate(request, username=user_match.username, password=password)
            except User.DoesNotExist:
                user_obj = None
        else:
            user_obj = authenticate(request, username=username_or_email, password=password)

        if user_obj is not None:
            if not user_obj.is_active:
                messages.error(request, "Your account has been deactivated. Please contact support.")
            else:
                login(request, user_obj)
                messages.success(request, f"Welcome back, {user_obj.get_full_name() or user_obj.username}!")
                next_url = request.GET.get('next', 'feed')
                return redirect(next_url)
        else:
            messages.error(request, "Invalid username/email or password.")
    return render(request, 'auth/login.html')


def logout_view(request):
    logout(request)
    messages.info(request, "You have been logged out successfully.")
    return redirect('login')


def forgot_password_view(request):
    if request.method == 'POST':
        email = request.POST.get('email', '').strip()
        user_exists = User.objects.filter(email__iexact=email).exists()
        if user_exists:
            messages.success(request, f"Password reset instructions and verification code have been simulated for {email}.")
            return redirect('reset_password')
        else:
            messages.error(request, "No registered account found with that email address.")
    return render(request, 'auth/forgot_password.html')


def reset_password_view(request):
    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        new_password = request.POST.get('new_password', '')
        confirm_password = request.POST.get('confirm_password', '')
        if new_password != confirm_password:
            messages.error(request, "Passwords do not match.")
        elif len(new_password) < 6:
            messages.error(request, "Password must be at least 6 characters.")
        else:
            try:
                u = User.objects.get(username__iexact=username)
                u.set_password(new_password)
                u.save()
                messages.success(request, "Password successfully reset! You can now log in with your new password.")
                return redirect('login')
            except User.DoesNotExist:
                messages.error(request, "User not found.")
    return render(request, 'auth/reset_password.html')


# -------------------------------------------------------------
# 2. Home Feed & Post Management
# -------------------------------------------------------------
@login_required
def feed_view(request):
    tab = request.GET.get('tab', 'all')  # 'all', 'following', 'saved'
    user = request.user

    # Exclude blocked users
    blocked_ids = Block.objects.filter(Q(blocker=user) | Q(blocked=user)).values_list('blocker_id', 'blocked_id')
    excluded_user_ids = set()
    for b1, b2 in blocked_ids:
        excluded_user_ids.add(b1 if b1 != user.id else b2)

    posts_query = Post.objects.exclude(author_id__in=excluded_user_ids).select_related('author', 'author__profile', 'group', 'company').prefetch_related('likes', 'comments', 'saves')

    if tab == 'following':
        # Posts from connected users or followed users
        following_ids = list(Follow.objects.filter(follower=user).values_list('following_id', flat=True))
        connected_ids = set()
        for conn in Connection.objects.filter((Q(sender=user) | Q(receiver=user)), status='accepted'):
            connected_ids.add(conn.receiver_id if conn.sender_id == user.id else conn.sender_id)
        target_ids = set(following_ids).union(connected_ids)
        target_ids.add(user.id)
        posts_query = posts_query.filter(author_id__in=target_ids)
    elif tab == 'saved':
        posts_query = posts_query.filter(saves=user)
    else:
        # 'all' feed - public posts or own posts or connections posts
        posts_query = posts_query.filter(
            Q(visibility='public') | Q(author=user)
        )

    posts = posts_query[:50]
    post_form = PostForm()
    comment_form = CommentForm()

    # Recommended people to connect
    suggested_users = User.objects.exclude(id=user.id).exclude(id__in=excluded_user_ids).select_related('profile')[:5]

    # Trending groups
    popular_groups = Group.objects.annotate(num_members=Count('members')).order_by('-num_members')[:4]

    # Trending companies
    featured_companies = CompanyPage.objects.annotate(num_followers=Count('followers')).order_by('-num_followers')[:4]

    context = {
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
        'active_tab': tab,
        'suggested_users': suggested_users,
        'popular_groups': popular_groups,
        'featured_companies': featured_companies,
    }
    return render(request, 'feed/home_feed.html', context)


@login_required
@require_POST
def create_post_view(request):
    if not request.user.profile.check_permission('can_create_posts'):
        messages.error(request, "Permission Denied: Your account does not have authority to create posts.")
        return redirect('feed')
    if request.FILES.get('image') and not request.user.profile.check_permission('can_upload_photos'):
        messages.error(request, "Permission Denied: Your account does not have authority to upload photos.")
        return redirect('feed')

    form = PostForm(request.POST, request.FILES)
    group_id = request.POST.get('group_id')
    company_id = request.POST.get('company_id')

    if form.is_valid():
        post = form.save(commit=False)
        post.author = request.user
        if group_id:
            try:
                post.group = Group.objects.get(id=group_id)
            except Group.DoesNotExist:
                pass
        if company_id:
            try:
                company = CompanyPage.objects.get(id=company_id)
                if company.owner == request.user:
                    post.company = company
            except CompanyPage.DoesNotExist:
                pass
        post.save()
        messages.success(request, "Your post has been published to the community!")
    else:
        messages.error(request, "Could not publish post. Please check the content.")

    if group_id:
        return redirect('group_detail', slug=post.group.slug)
    if company_id:
        return redirect('company_detail', slug=post.company.slug)
    return redirect('feed')


@login_required
def post_detail_view(request, post_id):
    post = get_object_or_404(Post.objects.select_related('author', 'author__profile'), id=post_id)
    comment_form = CommentForm()
    comments = post.comments.filter(parent__isnull=True).select_related('author', 'author__profile').prefetch_related('replies__author__profile')
    return render(request, 'feed/post_detail.html', {
        'post': post,
        'comments': comments,
        'comment_form': comment_form,
    })


@login_required
def edit_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id, author=request.user)
    if not request.user.profile.check_permission('can_edit_posts'):
        messages.error(request, "Permission Denied: Your account does not have authority to edit posts.")
        return redirect('post_detail', post_id=post.id)

    if request.method == 'POST':
        form = PostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            form.save()
            messages.success(request, "Post updated successfully!")
            return redirect('post_detail', post_id=post.id)
    else:
        form = PostForm(instance=post)
    return render(request, 'feed/edit_post.html', {'form': form, 'post': post})


@login_required
@require_POST
def delete_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    can_delete = (post.author == request.user and request.user.profile.check_permission('can_delete_posts')) or request.user.profile.is_moderator_or_higher()
    if can_delete:
        post.delete()
        messages.success(request, "Post deleted successfully.")
    else:
        messages.error(request, "Permission Denied: You are not authorized to delete this post.")
    return redirect('feed')


@login_required
@require_POST
def toggle_like_view(request, post_id):
    if not request.user.profile.check_permission('can_like_posts'):
        return JsonResponse({'error': 'Permission denied: Cannot like posts.'}, status=403)

    post = get_object_or_404(Post, id=post_id)
    user = request.user
    liked = False
    if post.likes.filter(id=user.id).exists():
        post.likes.remove(user)
    else:
        post.likes.add(user)
        liked = True
        create_notification(
            recipient=post.author,
            sender=user,
            notification_type='like',
            text=f"{user.get_full_name() or user.username} liked your post.",
            link=f"/post/{post.id}/",
            post=post
        )
    return JsonResponse({'liked': liked, 'likes_count': post.likes_count()})


@login_required
@require_POST
def toggle_save_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    user = request.user
    saved = False
    if post.saves.filter(id=user.id).exists():
        post.saves.remove(user)
    else:
        post.saves.add(user)
        saved = True
    return JsonResponse({'saved': saved})


@login_required
@require_POST
def add_comment_view(request, post_id):
    if not request.user.profile.check_permission('can_comment_posts'):
        messages.error(request, "Permission Denied: Your account does not have authority to comment on posts.")
        return redirect('post_detail', post_id=post_id)

    post = get_object_or_404(Post, id=post_id)
    content = request.POST.get('content', '').strip()
    parent_id = request.POST.get('parent_id')

    if content:
        parent_comment = None
        if parent_id:
            try:
                parent_comment = Comment.objects.get(id=parent_id, post=post)
            except Comment.DoesNotExist:
                pass

        comment = Comment.objects.create(
            post=post,
            author=request.user,
            content=content,
            parent=parent_comment
        )

        # Send notification to post author or parent comment author
        if parent_comment and parent_comment.author != request.user:
            create_notification(
                recipient=parent_comment.author,
                sender=request.user,
                notification_type='reply',
                text=f"{request.user.get_full_name() or request.user.username} replied to your comment.",
                link=f"/post/{post.id}/",
                post=post
            )
        elif post.author != request.user:
            create_notification(
                recipient=post.author,
                sender=request.user,
                notification_type='comment',
                text=f"{request.user.get_full_name() or request.user.username} commented on your post.",
                link=f"/post/{post.id}/",
                post=post
            )

        messages.success(request, "Comment added.")
    return redirect('post_detail', post_id=post.id)


@login_required
@require_POST
def delete_comment_view(request, comment_id):
    comment = get_object_or_404(Comment, id=comment_id)
    post_id = comment.post.id
    if comment.author == request.user or comment.post.author == request.user or request.user.is_staff:
        comment.delete()
        messages.success(request, "Comment deleted.")
    return redirect('post_detail', post_id=post_id)


@login_required
@require_POST
def share_post_view(request, post_id):
    post = get_object_or_404(Post, id=post_id)
    post.shares_count += 1
    post.save()
    messages.success(request, "Post shared successfully to your network!")
    return redirect('feed')


# -------------------------------------------------------------
# 3. User Profile Management
# -------------------------------------------------------------
def profile_view(request, username):
    profile_user = get_object_or_404(User.objects.select_related('profile'), username__iexact=username)
    profile = profile_user.profile

    # Check privacy
    is_me = (request.user.is_authenticated and request.user == profile_user)
    is_connected = False
    has_pending = False
    has_sent = False
    is_following = False

    if request.user.is_authenticated and not is_me:
        is_connected = request.user.profile.is_connected_with(profile_user)
        has_pending = request.user.profile.has_pending_request_from(profile_user)
        has_sent = request.user.profile.has_sent_request_to(profile_user)
        is_following = request.user.profile.is_following(profile_user)

    # Check if viewer can see posts
    can_view_posts = True
    if profile.is_private and not is_me and not is_connected:
        can_view_posts = False

    posts = []
    if can_view_posts:
        posts = Post.objects.filter(author=profile_user).select_related('author', 'author__profile')

    mutual_conns = []
    if request.user.is_authenticated and not is_me:
        mutual_conns = request.user.profile.mutual_connections(profile_user)

    context = {
        'profile_user': profile_user,
        'profile': profile,
        'is_me': is_me,
        'is_connected': is_connected,
        'has_pending': has_pending,
        'has_sent': has_sent,
        'is_following': is_following,
        'can_view_posts': can_view_posts,
        'posts': posts,
        'mutual_connections': mutual_conns,
    }
    return render(request, 'profile/profile_detail.html', context)


@login_required
def edit_profile_view(request):
    if not request.user.profile.check_permission('can_edit_profile'):
        messages.error(request, "Permission Denied: Your account does not have authority to edit profile.")
        return redirect('profile', username=request.user.username)

    profile = request.user.profile
    if request.method == 'POST':
        form = UserProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            # update User first/last name
            first_name = form.cleaned_data.get('first_name')
            last_name = form.cleaned_data.get('last_name')
            if first_name is not None:
                request.user.first_name = first_name
            if last_name is not None:
                request.user.last_name = last_name
            request.user.save()

            messages.success(request, "Your profile has been updated successfully!")
            return redirect('profile', username=request.user.username)
    else:
        initial = {
            'first_name': request.user.first_name,
            'last_name': request.user.last_name,
        }
        form = UserProfileForm(instance=profile, initial=initial)
    return render(request, 'profile/edit_profile.html', {'form': form})


@login_required
def deactivate_account_view(request):
    profile = request.user.profile
    deletion_request = AccountDeletionRequest.objects.filter(user=request.user).first()
    deletion_form = AccountDeletionRequestForm()

    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'deactivate':
            profile.account_status = 'INACTIVE'
            profile.is_active_account = False
            profile.save()
            request.user.is_active = False
            request.user.save()
            logout(request)
            messages.info(request, "Your account has been deactivated. You can reactivate by contacting support.")
            return redirect('login')

    return render(request, 'profile/deactivate.html', {
        'profile': profile,
        'deletion_request': deletion_request,
        'deletion_form': deletion_form,
    })


@login_required
def request_account_deletion_view(request):
    existing_request = AccountDeletionRequest.objects.filter(
        user=request.user,
        status__in=['PENDING', 'UNDER_REVIEW', 'APPROVED']
    ).first()

    if existing_request:
        messages.warning(request, f"You already have an active deletion request with status '{existing_request.get_status_display()}'.")
        return redirect('deactivate_account')

    if request.method == 'POST':
        form = AccountDeletionRequestForm(request.POST)
        if form.is_valid():
            del_req = form.save(commit=False)
            del_req.user = request.user
            del_req.status = 'PENDING'
            del_req.save()

            profile = request.user.profile
            profile.account_status = 'PENDING_DELETION'
            profile.save()

            # In-app notification
            create_notification(
                recipient=request.user,
                sender=request.user,
                notification_type='deletion',
                text="Your account deletion request has been submitted. Status: Pending.",
                link='/account/deactivate/'
            )

            # Audit log
            log_admin_action(
                request,
                "DELETION_REQUEST_SUBMITTED",
                module_name="Account Deletion",
                affected_user=request.user,
                new_val=del_req.reason
            )

            messages.success(request, "Your account deletion request has been successfully submitted. The administration team will review it.")
            return redirect('deactivate_account')

    return redirect('deactivate_account')


@login_required
def cancel_account_deletion_view(request, request_id):
    del_req = get_object_or_404(AccountDeletionRequest, id=request_id, user=request.user)
    if del_req.status in ('COMPLETED', 'CANCELLED'):
        messages.error(request, "This deletion request cannot be cancelled.")
        return redirect('deactivate_account')

    del_req.status = 'CANCELLED'
    del_req.save()

    profile = request.user.profile
    profile.account_status = 'ACTIVE'
    profile.save()

    create_notification(
        recipient=request.user,
        sender=request.user,
        notification_type='deletion',
        text="You have cancelled your account deletion request. Your account remains Active.",
        link='/account/deactivate/'
    )

    log_admin_action(
        request,
        "DELETION_REQUEST_CANCELLED_BY_USER",
        module_name="Account Deletion",
        affected_user=request.user
    )

    messages.success(request, "Your account deletion request has been cancelled. Your account remains active!")
    return redirect('deactivate_account')


# -------------------------------------------------------------
# 4. Connect With People & Follow System
# -------------------------------------------------------------
@login_required
def network_view(request):
    user = request.user
    tab = request.GET.get('tab', 'discover')

    # Pending connection requests
    received_requests = Connection.objects.filter(receiver=user, status='pending').select_related('sender', 'sender__profile')
    sent_requests = Connection.objects.filter(sender=user, status='pending').select_related('receiver', 'receiver__profile')

    # Connected users
    accepted_connections = Connection.objects.filter(
        (Q(sender=user) | Q(receiver=user)), status='accepted'
    ).select_related('sender__profile', 'receiver__profile')

    connected_users = []
    for conn in accepted_connections:
        connected_users.append(conn.receiver if conn.sender == user else conn.sender)

    # Followers & Following
    followers = [f.follower for f in Follow.objects.filter(following=user).select_related('follower__profile')]
    following = [f.following for f in Follow.objects.filter(follower=user).select_related('following__profile')]

    # Discover people
    connected_ids = [u.id for u in connected_users]
    connected_ids.append(user.id)
    discover_people = User.objects.exclude(id__in=connected_ids).select_related('profile')[:20]

    context = {
        'tab': tab,
        'received_requests': received_requests,
        'sent_requests': sent_requests,
        'connected_users': connected_users,
        'followers': followers,
        'following': following,
        'discover_people': discover_people,
    }
    return render(request, 'network/network.html', context)


@login_required
@require_POST
def send_connection_view(request, user_id):
    if not request.user.profile.check_permission('can_send_friend_requests'):
        messages.error(request, "Permission Denied: Your account does not have authority to send connection requests.")
        return redirect('network')

    target = get_object_or_404(User, id=user_id)
    if target != request.user:
        conn, created = Connection.objects.get_or_create(
            sender=request.user, receiver=target,
            defaults={'status': 'pending'}
        )
        if not created and conn.status == 'rejected':
            conn.status = 'pending'
            conn.save()

        create_notification(
            recipient=target,
            sender=request.user,
            notification_type='connection_request',
            text=f"{request.user.get_full_name() or request.user.username} sent you a connection request.",
            link="/network/?tab=requests"
        )
        messages.success(request, f"Connection request sent to {target.get_full_name() or target.username}.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def accept_connection_view(request, connection_id):
    if not request.user.profile.check_permission('can_accept_friend_requests'):
        messages.error(request, "Permission Denied: Your account does not have authority to accept connection requests.")
        return redirect('network')

    conn = get_object_or_404(Connection, id=connection_id, receiver=request.user)
    conn.status = 'accepted'
    conn.save()

    create_notification(
        recipient=conn.sender,
        sender=request.user,
        notification_type='connection_accepted',
        text=f"{request.user.get_full_name() or request.user.username} accepted your connection request.",
        link=f"/profile/{request.user.username}/"
    )
    messages.success(request, f"You are now connected with {conn.sender.get_full_name() or conn.sender.username}!")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def reject_connection_view(request, connection_id):
    conn = get_object_or_404(Connection, id=connection_id, receiver=request.user)
    conn.status = 'rejected'
    conn.save()
    messages.info(request, "Connection request declined.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def remove_connection_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    Connection.objects.filter(
        (Q(sender=request.user, receiver=target) | Q(sender=target, receiver=request.user)),
        status='accepted'
    ).delete()
    messages.info(request, f"Connection removed with {target.username}.")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def toggle_follow_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    if target == request.user:
        return redirect('network')

    follow_rel = Follow.objects.filter(follower=request.user, following=target)
    if follow_rel.exists():
        follow_rel.delete()
        messages.info(request, f"You unfollowed {target.username}.")
    else:
        Follow.objects.create(follower=request.user, following=target)
        create_notification(
            recipient=target,
            sender=request.user,
            notification_type='follow',
            text=f"{request.user.get_full_name() or request.user.username} started following you.",
            link=f"/profile/{request.user.username}/"
        )
        messages.success(request, f"You are now following {target.username}!")
    return redirect(request.META.get('HTTP_REFERER', 'network'))


@login_required
@require_POST
def block_user_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    if target != request.user:
        Block.objects.get_or_create(blocker=request.user, blocked=target)
        # remove connection / follow
        Connection.objects.filter(
            (Q(sender=request.user, receiver=target) | Q(sender=target, receiver=request.user))
        ).delete()
        Follow.objects.filter(
            (Q(follower=request.user, following=target) | Q(follower=target, following=request.user))
        ).delete()
        messages.warning(request, f"You blocked {target.username}.")
    return redirect('network')


@login_required
@require_POST
def unblock_user_view(request, user_id):
    target = get_object_or_404(User, id=user_id)
    Block.objects.filter(blocker=request.user, blocked=target).delete()
    messages.success(request, f"You unblocked {target.username}.")
    return redirect('network')


# -------------------------------------------------------------
# 5. Messaging / Chat System
# -------------------------------------------------------------
@login_required
def messages_view(request, username=None):
    if not request.user.profile.check_permission('can_send_messages'):
        messages.error(request, "Permission Denied: Your account does not have authority to use messaging.")
        return redirect('feed')

    user = request.user
    active_chat_user = None

    if username:
        active_chat_user = get_object_or_404(User.objects.select_related('profile'), username__iexact=username)
        # Mark unread messages from this user as read
        Message.objects.filter(sender=active_chat_user, receiver=user, is_read=False).update(is_read=True)

    # All conversations user participated in
    sent_receivers = Message.objects.filter(sender=user).values_list('receiver_id', flat=True)
    received_senders = Message.objects.filter(receiver=user).values_list('sender_id', flat=True)
    all_partner_ids = set(sent_receivers).union(set(received_senders))
    if active_chat_user:
        all_partner_ids.add(active_chat_user.id)

    conversations = []
    for pid in all_partner_ids:
        partner = User.objects.get(id=pid)
        last_msg = Message.objects.filter(
            (Q(sender=user, receiver=partner) | Q(sender=partner, receiver=user))
        ).order_by('-created_at').first()
        unread_count = Message.objects.filter(sender=partner, receiver=user, is_read=False).count()
        conversations.append({
            'partner': partner,
            'last_message': last_msg,
            'unread_count': unread_count,
        })

    conversations.sort(key=lambda c: c['last_message'].created_at if c['last_message'] else None, reverse=True)

    chat_messages = []
    if active_chat_user:
        chat_messages = Message.objects.filter(
            (Q(sender=user, receiver=active_chat_user) | Q(sender=active_chat_user, receiver=user))
        ).order_by('created_at')

    message_form = MessageForm()

    context = {
        'conversations': conversations,
        'active_chat_user': active_chat_user,
        'chat_messages': chat_messages,
        'message_form': message_form,
    }
    return render(request, 'chat/messages.html', context)


@login_required
@require_POST
def send_message_api(request, username):
    if not request.user.profile.check_permission('can_send_messages'):
        return JsonResponse({'status': 'error', 'message': 'Permission denied: Cannot send messages.'}, status=403)

    recipient = get_object_or_404(User, username__iexact=username)
    content = request.POST.get('content', '').strip()
    attachment = request.FILES.get('attachment')

    if content or attachment:
        msg = Message.objects.create(
            sender=request.user,
            receiver=recipient,
            content=content,
            attachment=attachment
        )
        create_notification(
            recipient=recipient,
            sender=request.user,
            notification_type='message',
            text=f"New message from {request.user.get_full_name() or request.user.username}.",
            link=f"/messages/{request.user.username}/"
        )
        return JsonResponse({
            'status': 'success',
            'id': msg.id,
            'content': msg.content,
            'created_at': msg.created_at.strftime('%H:%M'),
            'sender_username': request.user.username,
        })
    return JsonResponse({'status': 'error', 'message': 'Empty message'}, status=400)


@login_required
def get_messages_api(request, username):
    partner = get_object_or_404(User, username__iexact=username)
    last_id = int(request.GET.get('last_id', 0))

    new_messages = Message.objects.filter(
        (Q(sender=request.user, receiver=partner) | Q(sender=partner, receiver=request.user)),
        id__gt=last_id
    ).order_by('created_at')

    # Mark as read
    Message.objects.filter(sender=partner, receiver=request.user, is_read=False).update(is_read=True)

    data = []
    for m in new_messages:
        data.append({
            'id': m.id,
            'sender': m.sender.username,
            'is_me': m.sender == request.user,
            'content': m.content,
            'attachment_url': m.attachment.url if m.attachment else None,
            'time': m.created_at.strftime('%H:%M'),
        })
    return JsonResponse({'messages': data})


# -------------------------------------------------------------
# 6. Notifications
# -------------------------------------------------------------
@login_required
def notifications_view(request):
    notifications = Notification.objects.filter(recipient=request.user).select_related('sender', 'sender__profile', 'post')
    return render(request, 'notifications/notifications.html', {'notifications': notifications})


@login_required
def mark_notification_read_view(request, notification_id):
    notif = get_object_or_404(Notification, id=notification_id, recipient=request.user)
    notif.is_read = True
    notif.save()
    if notif.link:
        return redirect(notif.link)
    return redirect('notifications')


@login_required
@require_POST
def mark_all_notifications_read_view(request):
    Notification.objects.filter(recipient=request.user, is_read=False).update(is_read=True)
    messages.success(request, "All notifications marked as read.")
    return redirect('notifications')


# -------------------------------------------------------------
# 7. Global Search
# -------------------------------------------------------------
def search_view(request):
    query = request.GET.get('q', '').strip()
    category = request.GET.get('category', 'all')  # all, people, posts, groups, companies

    people = []
    posts = []
    groups = []
    companies = []

    if query:
        if category in ('all', 'people'):
            people = User.objects.filter(
                Q(username__icontains=query) |
                Q(first_name__icontains=query) |
                Q(last_name__icontains=query) |
                Q(profile__profession__icontains=query) |
                Q(profile__company_name__icontains=query) |
                Q(profile__skills__icontains=query)
            ).select_related('profile').distinct()[:20]

        if category in ('all', 'posts'):
            posts = Post.objects.filter(
                Q(content__icontains=query),
                visibility='public'
            ).select_related('author', 'author__profile')[:20]

        if category in ('all', 'groups'):
            groups = Group.objects.filter(
                Q(name__icontains=query) | Q(description__icontains=query)
            )[:15]

        if category in ('all', 'companies'):
            companies = CompanyPage.objects.filter(
                Q(name__icontains=query) | Q(tagline__icontains=query) | Q(industry__icontains=query)
            )[:15]

    context = {
        'query': query,
        'category': category,
        'people': people,
        'posts': posts,
        'groups': groups,
        'companies': companies,
        'total_results': len(people) + len(posts) + len(groups) + len(companies),
    }
    return render(request, 'search/search_results.html', context)


# -------------------------------------------------------------
# 8. Groups & Communities
# -------------------------------------------------------------
@login_required
def groups_list_view(request):
    all_groups = Group.objects.annotate(num_members=Count('members')).order_by('-num_members')
    my_groups = request.user.joined_groups.all()
    group_form = GroupForm()
    return render(request, 'groups/groups_list.html', {
        'all_groups': all_groups,
        'my_groups': my_groups,
        'group_form': group_form,
    })


@login_required
@require_POST
def create_group_view(request):
    if not request.user.profile.check_permission('can_create_communities'):
        messages.error(request, "Permission Denied: Your account does not have authority to create communities.")
        return redirect('groups_list')

    form = GroupForm(request.POST, request.FILES)
    if form.is_valid():
        group = form.save(commit=False)
        group.creator = request.user
        group.save()
        group.members.add(request.user)
        messages.success(request, f"Community '{group.name}' created successfully!")
        return redirect('group_detail', slug=group.slug)
    messages.error(request, "Error creating community. Please check fields.")
    return redirect('groups_list')


@login_required
def group_detail_view(request, slug):
    group = get_object_or_404(Group.objects.prefetch_related('members'), slug=slug)
    is_member = group.members.filter(id=request.user.id).exists()
    is_creator = (group.creator == request.user)

    posts = Post.objects.filter(group=group).select_related('author', 'author__profile')
    post_form = PostForm()
    comment_form = CommentForm()

    return render(request, 'groups/group_detail.html', {
        'group': group,
        'is_member': is_member,
        'is_creator': is_creator,
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
    })


@login_required
@require_POST
def join_group_view(request, slug):
    if not request.user.profile.check_permission('can_join_communities'):
        messages.error(request, "Permission Denied: Your account does not have authority to join communities.")
        return redirect('groups_list')

    group = get_object_or_404(Group, slug=slug)
    if group.members.filter(id=request.user.id).exists():
        group.members.remove(request.user)
        messages.info(request, f"You left '{group.name}'.")
    else:
        group.members.add(request.user)
        messages.success(request, f"You joined '{group.name}'!")
    return redirect('group_detail', slug=slug)


# -------------------------------------------------------------
# 9. Company & Professional Pages
# -------------------------------------------------------------
@login_required
def companies_list_view(request):
    all_companies = CompanyPage.objects.annotate(num_followers=Count('followers')).order_by('-num_followers')
    my_companies = CompanyPage.objects.filter(owner=request.user)
    company_form = CompanyPageForm()
    return render(request, 'companies/companies_list.html', {
        'all_companies': all_companies,
        'my_companies': my_companies,
        'company_form': company_form,
    })


@login_required
@require_POST
def create_company_view(request):
    form = CompanyPageForm(request.POST, request.FILES)
    if form.is_valid():
        company = form.save(commit=False)
        company.owner = request.user
        company.save()
        company.followers.add(request.user)
        messages.success(request, f"Company Page '{company.name}' created successfully!")
        return redirect('company_detail', slug=company.slug)
    messages.error(request, "Error creating company page.")
    return redirect('companies_list')


@login_required
def company_detail_view(request, slug):
    company = get_object_or_404(CompanyPage.objects.prefetch_related('followers'), slug=slug)
    is_follower = company.followers.filter(id=request.user.id).exists()
    is_owner = (company.owner == request.user)

    posts = Post.objects.filter(company=company).select_related('author', 'author__profile')
    post_form = PostForm()
    comment_form = CommentForm()

    return render(request, 'companies/company_detail.html', {
        'company': company,
        'is_follower': is_follower,
        'is_owner': is_owner,
        'posts': posts,
        'post_form': post_form,
        'comment_form': comment_form,
    })


@login_required
@require_POST
def toggle_follow_company_view(request, slug):
    company = get_object_or_404(CompanyPage, slug=slug)
    if company.followers.filter(id=request.user.id).exists():
        company.followers.remove(request.user)
        messages.info(request, f"You unfollowed {company.name}.")
    else:
        company.followers.add(request.user)
        messages.success(request, f"You are now following {company.name}!")
    return redirect('company_detail', slug=slug)


# -------------------------------------------------------------
# 10. Comprehensive Admin Dashboard & Authority Governance
# -------------------------------------------------------------

def check_admin_access(user):
    if not user.is_authenticated:
        return False
    profile = getattr(user, 'profile', None)
    return user.is_superuser or user.is_staff or (profile and profile.is_admin_or_super())


def check_super_admin_access(user):
    if not user.is_authenticated:
        return False
    profile = getattr(user, 'profile', None)
    return user.is_superuser or (profile and profile.is_super_admin())


@login_required
def admin_dashboard_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access restricted to platform administrators.")
        return redirect('feed')

    # A. 10 Summary KPI Metrics
    total_users = User.objects.count()
    active_users = UserProfile.objects.filter(account_status='ACTIVE', user__is_active=True).count()
    inactive_users = UserProfile.objects.filter(Q(account_status='INACTIVE') | Q(user__is_active=False)).exclude(account_status='BLOCKED').count()
    blocked_users = UserProfile.objects.filter(account_status='BLOCKED').count()
    online_users = UserProfile.objects.filter(is_online=True).count()
    pending_deletions = AccountDeletionRequest.objects.filter(status__in=['PENDING', 'UNDER_REVIEW']).count()
    total_posts = Post.objects.count()
    total_photos = Post.objects.filter(image__isnull=False).exclude(image='').count()
    total_communities = Group.objects.count()
    total_friend_requests = Connection.objects.count()

    recent_users = User.objects.select_related('profile', 'profile__role').order_by('-date_joined')[:10]
    recent_deletions = AccountDeletionRequest.objects.filter(status__in=['PENDING', 'UNDER_REVIEW']).select_related('user', 'user__profile')[:5]
    recent_reports = ReportedContent.objects.filter(status='pending').select_related('reporter', 'post', 'reported_user')[:5]
    recent_logs = AdminActivityLog.objects.order_by('-created_at')[:8]

    context = {
        'total_users': total_users,
        'active_users': active_users,
        'inactive_users': inactive_users,
        'blocked_users': blocked_users,
        'online_users': online_users,
        'pending_deletions': pending_deletions,
        'total_posts': total_posts,
        'total_photos': total_photos,
        'total_communities': total_communities,
        'total_friend_requests': total_friend_requests,
        'recent_users': recent_users,
        'recent_deletions': recent_deletions,
        'recent_reports': recent_reports,
        'recent_logs': recent_logs,
        'is_super_admin': check_super_admin_access(request.user),
    }
    return render(request, 'admin_panel/dashboard.html', context)


# ----------------- B. USER MANAGEMENT -----------------

@login_required
def admin_users_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied: Administrator privileges required.")
        return redirect('feed')

    q = request.GET.get('q', '').strip()
    status_filter = request.GET.get('status', '')
    role_filter = request.GET.get('role', '')

    users_qs = User.objects.select_related('profile', 'profile__role').order_by('-date_joined')

    if q:
        users_qs = users_qs.filter(
            Q(username__icontains=q) |
            Q(email__icontains=q) |
            Q(first_name__icontains=q) |
            Q(last_name__icontains=q) |
            Q(profile__mobile_number__icontains=q) |
            Q(profile__company_name__icontains=q)
        )

    if status_filter:
        users_qs = users_qs.filter(profile__account_status=status_filter)

    if role_filter:
        users_qs = users_qs.filter(profile__role__id=role_filter)

    paginator = Paginator(users_qs, 15)
    page_number = request.GET.get('page')
    users_page = paginator.get_page(page_number)

    roles = UserRole.objects.all()

    return render(request, 'admin_panel/users.html', {
        'users_page': users_page,
        'search_query': q,
        'status_filter': status_filter,
        'role_filter': role_filter,
        'roles': roles,
        'is_super_admin': check_super_admin_access(request.user),
    })


@login_required
def admin_edit_user_view(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    profile, _ = UserProfile.objects.get_or_create(user=target_user)

    if target_user.is_superuser and not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can edit a Super Admin account.")
        return redirect('admin_users')

    if request.method == 'POST':
        form = AdminUserEditForm(request.POST, instance=profile)
        if form.is_valid():
            prev_status = profile.account_status
            prev_role = profile.role.name if profile.role else "None"

            target_user.email = form.cleaned_data['email']
            target_user.first_name = form.cleaned_data.get('first_name', '')
            target_user.last_name = form.cleaned_data.get('last_name', '')

            assigned_role = form.cleaned_data.get('role')
            if assigned_role and assigned_role.code == 'SUPER_ADMIN' and not check_super_admin_access(request.user):
                messages.error(request, "You do not have permission to grant Super Admin privileges.")
                return redirect('admin_users')

            target_user.save()
            updated_profile = form.save()

            new_status = updated_profile.account_status
            new_role = updated_profile.role.name if updated_profile.role else "None"

            log_admin_action(
                request,
                "USER_PROFILE_UPDATED",
                module_name="User Management",
                affected_user=target_user,
                prev_val=f"Status: {prev_status}, Role: {prev_role}",
                new_val=f"Status: {new_status}, Role: {new_role}"
            )

            messages.success(request, f"User @{target_user.username} updated successfully!")
            return redirect('admin_users')
    else:
        form = AdminUserEditForm(
            instance=profile,
            initial={
                'email': target_user.email,
                'first_name': target_user.first_name,
                'last_name': target_user.last_name,
            }
        )

    return render(request, 'admin_panel/edit_user.html', {
        'form': form,
        'target_user': target_user,
        'profile': profile,
    })


@login_required
def admin_toggle_user_status_view(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    if target_user.is_superuser:
        messages.error(request, "Cannot deactivate a Super Admin account.")
        return redirect('admin_users')

    profile = target_user.profile
    prev_status = profile.account_status
    if profile.account_status == 'ACTIVE':
        profile.account_status = 'INACTIVE'
        profile.is_active_account = False
        target_user.is_active = False
        action_name = "USER_DEACTIVATED"
        msg = f"User @{target_user.username} deactivated."
    else:
        profile.account_status = 'ACTIVE'
        profile.is_active_account = True
        target_user.is_active = True
        action_name = "USER_ACTIVATED"
        msg = f"User @{target_user.username} activated."

    profile.save()
    target_user.save()
    log_admin_action(request, action_name, module_name="User Management", affected_user=target_user, prev_val=prev_status, new_val=profile.account_status)
    messages.info(request, msg)
    return redirect('admin_users')


@login_required
def admin_toggle_user_block_view(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    if target_user.is_superuser:
        messages.error(request, "Cannot block a Super Admin account.")
        return redirect('admin_users')

    profile = target_user.profile
    prev_status = profile.account_status
    if profile.account_status == 'BLOCKED':
        profile.account_status = 'ACTIVE'
        profile.is_active_account = True
        target_user.is_active = True
        action_name = "USER_UNBLOCKED"
        msg = f"User @{target_user.username} has been unblocked and activated."
    else:
        profile.account_status = 'BLOCKED'
        profile.is_active_account = False
        target_user.is_active = False
        action_name = "USER_BLOCKED"
        msg = f"User @{target_user.username} has been blocked."

    profile.save()
    target_user.save()
    log_admin_action(request, action_name, module_name="User Management", affected_user=target_user, prev_val=prev_status, new_val=profile.account_status)
    messages.success(request, msg)
    return redirect('admin_users')


@login_required
def admin_reset_user_password_view(request, user_id):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can perform administrative password resets.")
        return redirect('admin_users')

    target_user = get_object_or_404(User, id=user_id)
    if request.method == 'POST':
        new_password = request.POST.get('new_password', '').strip()
        if len(new_password) >= 6:
            target_user.set_password(new_password)
            target_user.save()
            log_admin_action(request, "PASSWORD_RESET_BY_ADMIN", module_name="User Management", affected_user=target_user, new_val="Password reset executed")
            messages.success(request, f"Password for @{target_user.username} has been reset successfully.")
        else:
            messages.error(request, "Password must be at least 6 characters long.")
    return redirect('admin_users')


@login_required
def admin_delete_user_view(request, user_id):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can permanently delete user accounts.")
        return redirect('admin_users')

    target_user = get_object_or_404(User, id=user_id)
    if target_user == request.user:
        messages.error(request, "You cannot delete your own account while logged in as Super Admin.")
        return redirect('admin_users')

    username = target_user.username
    log_admin_action(request, "USER_DELETED_PERMANENTLY", module_name="User Management", affected_user=None, prev_val=f"Deleted user: @{username}")
    target_user.delete()
    messages.success(request, f"Account @{username} and all associated data have been permanently deleted.")
    return redirect('admin_users')


@login_required
def admin_user_activity_view(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    posts = Post.objects.filter(author=target_user)
    comments = Comment.objects.filter(author=target_user).select_related('post')
    messages_sent = Message.objects.filter(sender=target_user).select_related('receiver')
    messages_received = Message.objects.filter(receiver=target_user).select_related('sender')
    groups = target_user.joined_groups.all()
    audit_logs = AdminActivityLog.objects.filter(affected_user_id=target_user.id)

    return render(request, 'admin_panel/user_activity.html', {
        'target_user': target_user,
        'posts': posts,
        'comments': comments,
        'messages_sent': messages_sent,
        'messages_received': messages_received,
        'groups': groups,
        'audit_logs': audit_logs,
    })


# ----------------- C. USER AUTHORITIES & PERMISSION MANAGEMENT (18 AUTHORITIES) -----------------

@login_required
def admin_manage_authorities_view(request, user_id=None):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can manage individual user authorities.")
        return redirect('admin_dashboard')

    if user_id is None:
        return redirect('admin_users')

    target_user = get_object_or_404(User, id=user_id)
    custom_perm, _ = UserPermission.objects.get_or_create(user=target_user)

    action = request.GET.get('action')
    if action == 'grant_all':
        for field in PERM_FIELDS:
            setattr(custom_perm, field, True)
        custom_perm.save()
        log_admin_action(request, "USER_AUTHORITIES_GRANT_ALL", module_name="Authorities", affected_user=target_user, new_val="All 18 authorities ON")
        messages.success(request, f"All 18 authorities granted (ON) for @{target_user.username}!")
        return redirect('admin_manage_authorities', user_id=target_user.id)
    elif action == 'revoke_all':
        for field in PERM_FIELDS:
            setattr(custom_perm, field, False)
        custom_perm.save()
        log_admin_action(request, "USER_AUTHORITIES_REVOKE_ALL", module_name="Authorities", affected_user=target_user, new_val="All 18 authorities OFF")
        messages.warning(request, f"All 18 authorities revoked (OFF) for @{target_user.username}!")
        return redirect('admin_manage_authorities', user_id=target_user.id)
    elif action == 'restore_defaults':
        if target_user.profile.role and hasattr(target_user.profile.role, 'permissions'):
            role_perm = target_user.profile.role.permissions
            for field in PERM_FIELDS:
                setattr(custom_perm, field, getattr(role_perm, field, True))
        else:
            for field in PERM_FIELDS:
                setattr(custom_perm, field, True)
        custom_perm.save()
        log_admin_action(request, "USER_AUTHORITIES_RESTORE_DEFAULTS", module_name="Authorities", affected_user=target_user, new_val="Restored to Role Defaults")
        messages.info(request, f"Authorities for @{target_user.username} restored to system role defaults.")
        return redirect('admin_manage_authorities', user_id=target_user.id)

    if request.method == 'POST':
        form = UserPermissionForm(request.POST, instance=custom_perm)
        if form.is_valid():
            form.save()
            log_admin_action(
                request,
                "USER_AUTHORITIES_UPDATED",
                module_name="Authorities",
                affected_user=target_user,
                new_val="18 configurable authorities updated"
            )
            create_notification(
                recipient=target_user,
                sender=request.user,
                notification_type='system',
                text="An administrator has updated your account feature authorities.",
                link='/profile-edit/'
            )
            messages.success(request, f"Authorities and permissions for @{target_user.username} have been saved immediately!")
            return redirect('admin_manage_authorities', user_id=target_user.id)
    else:
        form = UserPermissionForm(instance=custom_perm)

    return render(request, 'admin_panel/manage_authorities.html', {
        'form': form,
        'target_user': target_user,
        'custom_perm': custom_perm,
    })


# ----------------- D. ROLE MANAGEMENT -----------------

@login_required
def admin_roles_view(request):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can manage system roles.")
        return redirect('admin_dashboard')

    roles = UserRole.objects.all().prefetch_related('permissions', 'users')
    new_role_form = UserRoleForm()

    if request.method == 'POST':
        form = UserRoleForm(request.POST)
        if form.is_valid():
            role = form.save()
            RolePermission.objects.get_or_create(role=role)
            log_admin_action(request, "ROLE_CREATED", module_name="Role Management", new_val=role.name)
            messages.success(request, f"New role '{role.name}' created successfully!")
            return redirect('admin_roles')

    return render(request, 'admin_panel/roles.html', {
        'roles': roles,
        'new_role_form': new_role_form,
    })


@login_required
def admin_edit_role_permissions_view(request, role_id):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can edit role authorities.")
        return redirect('admin_dashboard')

    role = get_object_or_404(UserRole, id=role_id)
    perm, _ = RolePermission.objects.get_or_create(role=role)

    if request.method == 'POST':
        form = RolePermissionForm(request.POST, instance=perm)
        if form.is_valid():
            form.save()
            log_admin_action(request, "ROLE_PERMISSIONS_UPDATED", module_name="Role Management", new_val=f"Role: {role.name}")
            messages.success(request, f"Default permissions for role '{role.name}' updated successfully!")
            return redirect('admin_roles')
    else:
        form = RolePermissionForm(instance=perm)

    return render(request, 'admin_panel/edit_role.html', {
        'role': role,
        'form': form,
    })


# ----------------- TASK 1 ADMIN: ACCOUNT DELETION REQUESTS -----------------

@login_required
def admin_deletion_requests_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied.")
        return redirect('feed')

    status_filter = request.GET.get('status', 'ALL')
    reqs = AccountDeletionRequest.objects.all().select_related('user', 'user__profile', 'reviewed_by').order_by('-created_at')

    if status_filter != 'ALL':
        reqs = reqs.filter(status=status_filter)

    return render(request, 'admin_panel/deletion_requests.html', {
        'deletion_requests': reqs,
        'status_filter': status_filter,
        'is_super_admin': check_super_admin_access(request.user),
    })


@login_required
def admin_handle_deletion_request_view(request, request_id, action):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    del_req = get_object_or_404(AccountDeletionRequest, id=request_id)
    prev_status = del_req.status
    notes = request.POST.get('admin_notes', '').strip()

    if action == 'under_review':
        del_req.status = 'UNDER_REVIEW'
        del_req.admin_notes = notes
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        create_notification(
            recipient=del_req.user,
            sender=request.user,
            notification_type='deletion',
            text=f"Your account deletion request is now Under Review by administrator @{request.user.username}.",
            link='/account/deactivate/'
        )
        log_admin_action(request, "DELETION_REQUEST_UNDER_REVIEW", module_name="Account Deletion", affected_user=del_req.user, prev_val=prev_status, new_val='UNDER_REVIEW')
        messages.info(request, f"Deletion request for @{del_req.user.username} marked as Under Review.")

    elif action == 'approve':
        del_req.status = 'APPROVED'
        del_req.admin_notes = notes
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        profile = del_req.user.profile
        profile.account_status = 'PENDING_DELETION'
        profile.save()

        create_notification(
            recipient=del_req.user,
            sender=request.user,
            notification_type='deletion',
            text=f"Your account deletion request has been Approved by @{request.user.username}. Account scheduled for permanent removal.",
            link='/account/deactivate/'
        )
        log_admin_action(request, "DELETION_REQUEST_APPROVED", module_name="Account Deletion", affected_user=del_req.user, prev_val=prev_status, new_val='APPROVED')
        messages.success(request, f"Deletion request for @{del_req.user.username} approved.")

    elif action == 'reject':
        del_req.status = 'REJECTED'
        del_req.admin_notes = notes
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        profile = del_req.user.profile
        profile.account_status = 'ACTIVE'
        profile.save()

        create_notification(
            recipient=del_req.user,
            sender=request.user,
            notification_type='deletion',
            text=f"Your account deletion request was Rejected. Note: {notes or 'No reason specified'}",
            link='/account/deactivate/'
        )
        log_admin_action(request, "DELETION_REQUEST_REJECTED", module_name="Account Deletion", affected_user=del_req.user, prev_val=prev_status, new_val='REJECTED')
        messages.info(request, f"Deletion request for @{del_req.user.username} rejected.")

    elif action == 'complete':
        if not check_super_admin_access(request.user):
            messages.error(request, "Only Super Administrators can execute complete account deletion.")
            return redirect('admin_deletion_requests')

        del_req.status = 'COMPLETED'
        del_req.admin_notes = notes or "Account data purged and deactivated per policy."
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        target_u = del_req.user
        target_u.is_active = False
        target_u.save()

        profile = target_u.profile
        profile.account_status = 'DELETED'
        profile.headline = "Deleted Member"
        profile.bio = "This account was deleted per user request in compliance with retention policies."
        profile.mobile_number = ""
        profile.whatsapp_number = ""
        profile.save()

        log_admin_action(request, "DELETION_REQUEST_COMPLETED", module_name="Account Deletion", affected_user=target_u, prev_val=prev_status, new_val='COMPLETED')
        messages.success(request, f"Account deletion completed for @{target_u.username}.")

    return redirect('admin_deletion_requests')


# ----------------- E. CONTENT MANAGEMENT -----------------

@login_required
def admin_content_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied.")
        return redirect('feed')

    tab = request.GET.get('tab', 'posts')
    posts = Post.objects.all().select_related('author', 'author__profile').order_by('-created_at')[:25]
    comments = Comment.objects.all().select_related('author', 'post').order_by('-created_at')[:25]
    photos = Post.objects.filter(image__isnull=False).exclude(image='').select_related('author', 'author__profile').order_by('-created_at')[:25]
    groups = Group.objects.all().select_related('creator').order_by('-created_at')[:25]
    reports = ReportedContent.objects.filter(status='pending').select_related('reporter', 'post', 'reported_user').order_by('-created_at')

    return render(request, 'admin_panel/content.html', {
        'tab': tab,
        'posts': posts,
        'comments': comments,
        'photos': photos,
        'groups': groups,
        'reports': reports,
    })


@login_required
def admin_delete_content_view(request, content_type, content_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    if content_type == 'post':
        p = get_object_or_404(Post, id=content_id)
        author = p.author
        p.delete()
        log_admin_action(request, "ADMIN_DELETED_POST", module_name="Content Management", affected_user=author)
        messages.success(request, "Post removed by admin.")
    elif content_type == 'comment':
        c = get_object_or_404(Comment, id=content_id)
        author = c.author
        c.delete()
        log_admin_action(request, "ADMIN_DELETED_COMMENT", module_name="Content Management", affected_user=author)
        messages.success(request, "Comment removed by admin.")
    elif content_type == 'group':
        g = get_object_or_404(Group, id=content_id)
        gname = g.name
        g.delete()
        log_admin_action(request, "ADMIN_DELETED_COMMUNITY", module_name="Content Management", new_val=gname)
        messages.success(request, f"Community '{gname}' deleted by admin.")

    return redirect(f"/admin-dashboard/content/?tab={content_type}s")


# ----------------- F. ADMIN ACTIVITY LOGS -----------------

@login_required
def admin_audit_logs_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied.")
        return redirect('feed')

    logs_qs = AdminActivityLog.objects.all().order_by('-created_at')
    action_filter = request.GET.get('action')
    if action_filter:
        logs_qs = logs_qs.filter(Q(action__icontains=action_filter) | Q(module_name__icontains=action_filter))

    paginator = Paginator(logs_qs, 20)
    page_number = request.GET.get('page')
    logs_page = paginator.get_page(page_number)

    return render(request, 'admin_panel/audit_logs.html', {
        'logs_page': logs_page,
        'action_filter': action_filter,
    })


@login_required
@require_POST
def report_content_view(request):
    post_id = request.POST.get('post_id')
    user_id = request.POST.get('user_id')
    reason = request.POST.get('reason', 'other')
    details = request.POST.get('details', '')

    post_obj = Post.objects.get(id=post_id) if post_id else None
    user_obj = User.objects.get(id=user_id) if user_id else None

    ReportedContent.objects.create(
        reporter=request.user,
        post=post_obj,
        reported_user=user_obj,
        reason=reason,
        details=details
    )
    messages.success(request, "Report submitted. Our moderation team will review this promptly.")
    return redirect(request.META.get('HTTP_REFERER', 'feed'))


@login_required
@require_POST
def admin_resolve_report_view(request, report_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()
    report = get_object_or_404(ReportedContent, id=report_id)
    action = request.POST.get('action', 'dismiss')
    if action == 'delete_post' and report.post:
        report.post.delete()
        report.status = 'resolved'
        log_admin_action(request, "REPORT_RESOLVED_DELETED_POST", module_name="Moderation", new_val=f"Report #{report.id}")
    elif action == 'deactivate_user' and report.reported_user:
        report.reported_user.is_active = False
        report.reported_user.save()
        report.status = 'resolved'
        log_admin_action(request, "REPORT_RESOLVED_DEACTIVATED_USER", module_name="Moderation", affected_user=report.reported_user)
    else:
        report.status = 'dismissed'
        log_admin_action(request, "REPORT_DISMISSED", module_name="Moderation", new_val=f"Report #{report.id}")
    report.save()
    messages.success(request, f"Report #{report.id} marked as {report.status}.")
    return redirect('admin_dashboard')
