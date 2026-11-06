from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import login, authenticate, logout
from django.contrib.auth.models import User
from django.contrib.auth.decorators import login_required
from django.contrib import messages
from django.http import JsonResponse, HttpResponseForbidden
from django.db.models import Q, Count
from django.utils import timezone
from django.core.paginator import Paginator

from .models import (
    UserProfile, UserRole, RolePermission, UserPermission, AccountDeletionRequest, AdminActivityLog,
    FriendRequest, Friendship, StatusPost, PostLike, PostComment, ScrapbookMessage,
    Community, CommunityMember, CommunityPost, CommunityComment, PhotoAlbum, Photo, PhotoComment,
    PrivateMessage, ProfileVisitor, Fan, Notification, Report
)
from .forms import (
    UserRegistrationForm, UserProfileForm, AccountDeletionRequestForm, UserRoleForm,
    RolePermissionForm, UserPermissionForm, AdminUserEditForm, StatusPostForm, ScrapForm,
    CommunityForm, CommunityPostForm, PhotoAlbumForm, PhotoUploadForm, ReportForm
)

# ----------------- AUDIT LOGGER HELPER -----------------

def log_admin_action(request, action, affected_user=None, prev_val="", new_val=""):
    ip = request.META.get('REMOTE_ADDR', '')
    AdminActivityLog.objects.create(
        admin_user=request.user if request.user.is_authenticated else None,
        action=action,
        affected_user=affected_user,
        previous_value=str(prev_val)[:500],
        updated_value=str(new_val)[:500],
        ip_address=ip
    )

# ----------------- AUTHENTICATION -----------------

def register_view(request):
    if request.user.is_authenticated:
        return redirect('home')
    if request.method == 'POST':
        form = UserRegistrationForm(request.POST, request.FILES)
        if form.is_valid():
            user = form.save(commit=False)
            user.set_password(form.cleaned_data['password'])
            user.first_name = form.cleaned_data.get('full_name', '')
            user.save()

            # Assign default Normal User role
            normal_role, _ = UserRole.objects.get_or_create(code='NORMAL_USER', defaults={'name': 'Normal User', 'is_system_role': True})

            profile, _ = UserProfile.objects.get_or_create(user=user)
            profile.role = normal_role
            profile.account_status = 'ACTIVE'
            profile.full_name = form.cleaned_data.get('full_name', '')
            profile.mobile_number = form.cleaned_data.get('mobile_number', '')
            profile.gender = form.cleaned_data.get('gender', 'M')
            profile.date_of_birth = form.cleaned_data.get('date_of_birth')
            profile.city = form.cleaned_data.get('city', 'Mumbai')
            if 'profile_photo' in request.FILES:
                profile.profile_photo = request.FILES['profile_photo']
            profile.is_online = True
            profile.save()

            # Initialize custom permissions
            UserPermission.objects.get_or_create(user=user)

            login(request, user)
            messages.success(request, f"Welcome to Orkut, {profile.full_name or user.username}! Your profile is ready.")
            return redirect('home')
    else:
        form = UserRegistrationForm()
    return render(request, 'auth/register.html', {'form': form})


def login_view(request):
    if request.user.is_authenticated:
        return redirect('home')
    if request.method == 'POST':
        login_input = request.POST.get('username', '').strip()
        password = request.POST.get('password', '')

        user_obj = None
        if '@' in login_input:
            user_obj = User.objects.filter(email__iexact=login_input).first()
        else:
            user_obj = User.objects.filter(username__iexact=login_input).first()

        if user_obj:
            user = authenticate(request, username=user_obj.username, password=password)
            if user:
                profile, _ = UserProfile.objects.get_or_create(user=user)
                if profile.account_status in ('BLOCKED', 'DELETED') or profile.is_suspended:
                    messages.error(request, f"Your Orkut account is {profile.account_status.lower()}. Please contact the administrator.")
                    return render(request, 'auth/login.html')
                login(request, user)
                profile.is_online = True
                profile.last_seen = timezone.now()
                profile.save()
                next_url = request.GET.get('next', 'home')
                return redirect(next_url)
        messages.error(request, "Invalid username/email or password.")
    return render(request, 'auth/login.html')


@login_required
def logout_view(request):
    if hasattr(request.user, 'orkut_profile'):
        request.user.orkut_profile.is_online = False
        request.user.orkut_profile.last_seen = timezone.now()
        request.user.orkut_profile.save()
    logout(request)
    messages.info(request, "You have been logged out of Orkut. See you soon!")
    return redirect('login')


def forgot_password_view(request):
    if request.method == 'POST':
        email = request.POST.get('email', '').strip()
        user = User.objects.filter(email__iexact=email).first()
        if user:
            messages.success(request, f"Password reset instructions have been generated for {email}.")
            return redirect('reset_password', username=user.username)
        else:
            messages.error(request, "No account found with this email address.")
    return render(request, 'auth/forgot_password.html')


def reset_password_view(request, username):
    user = get_object_or_404(User, username=username)
    if request.method == 'POST':
        new_pass = request.POST.get('password')
        conf_pass = request.POST.get('confirm_password')
        if new_pass and new_pass == conf_pass:
            user.set_password(new_pass)
            user.save()
            messages.success(request, "Password has been successfully updated! Please log in.")
            return redirect('login')
        else:
            messages.error(request, "Passwords do not match or are invalid.")
    return render(request, 'auth/reset_password.html', {'target_user': user})


# ----------------- HOME DASHBOARD -----------------

@login_required
def home_view(request):
    post_form = StatusPostForm()
    
    friendships = Friendship.objects.filter(Q(user1=request.user) | Q(user2=request.user))
    friend_ids = [f.user2_id if f.user1_id == request.user.id else f.user1_id for f in friendships]
    
    posts = StatusPost.objects.filter(
        Q(author=request.user) | Q(author_id__in=friend_ids)
    ).select_related('author', 'author__orkut_profile').prefetch_related('likes', 'comments', 'comments__author', 'comments__author__orkut_profile')[:30]

    sent_reqs = FriendRequest.objects.filter(from_user=request.user).values_list('to_user_id', flat=True)
    recv_reqs = FriendRequest.objects.filter(to_user=request.user).values_list('from_user_id', flat=True)
    excluded_ids = set(friend_ids) | set(sent_reqs) | set(recv_reqs) | {request.user.id}
    
    suggestions = User.objects.exclude(id__in=excluded_ids).select_related('orkut_profile')[:6]
    recent_visitors = ProfileVisitor.objects.filter(user=request.user).select_related('visitor', 'visitor__orkut_profile')[:6]
    profile = request.user.orkut_profile

    return render(request, 'dashboard/home.html', {
        'post_form': post_form,
        'posts': posts,
        'suggestions': suggestions,
        'recent_visitors': recent_visitors,
        'profile': profile,
    })


@login_required
def create_status_view(request):
    if not request.user.orkut_profile.check_permission('can_create_posts'):
        messages.error(request, "Your account does not have permission to publish posts.")
        return redirect('home')

    if request.method == 'POST':
        form = StatusPostForm(request.POST, request.FILES)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.save()
            messages.success(request, "Status updated successfully!")
    return redirect('home')


@login_required
def edit_status_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id, author=request.user)
    if not request.user.orkut_profile.check_permission('can_manage_posts'):
        messages.error(request, "Your account does not have permission to edit posts.")
        return redirect('home')

    if request.method == 'POST':
        form = StatusPostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            form.save()
            messages.success(request, "Post updated!")
            return redirect('home')
    else:
        form = StatusPostForm(instance=post)
    return render(request, 'dashboard/edit_post.html', {'form': form, 'post': post})


@login_required
def delete_status_view(request, post_id):
    post = get_object_or_404(StatusPost, id=post_id)
    if post.author == request.user or request.user.orkut_profile.is_moderator_or_higher():
        post.delete()
        messages.success(request, "Post deleted successfully.")
    else:
        messages.error(request, "You are not authorized to delete this post.")
    return redirect('home')


@login_required
def toggle_like_view(request, post_id):
    if not request.user.orkut_profile.check_permission('can_like_comment'):
        messages.error(request, "Your account does not have permission to like posts.")
        return redirect('home')

    post = get_object_or_404(StatusPost, id=post_id)
    like_obj = PostLike.objects.filter(post=post, user=request.user).first()
    liked = False
    if like_obj:
        like_obj.delete()
        liked = False
    else:
        PostLike.objects.create(post=post, user=request.user)
        liked = True
        if post.author != request.user:
            Notification.objects.create(
                recipient=post.author,
                sender=request.user,
                notification_type='LIKE',
                title="Post Liked",
                message=f"{request.user.orkut_profile.full_name or request.user.username} liked your status update.",
                link='/#post-' + str(post.id)
            )
    if request.headers.get('x-requested-with') == 'XMLHttpRequest':
        return JsonResponse({'liked': liked, 'total_likes': post.likes.count()})
    return redirect('home')


@login_required
def add_comment_view(request, post_id):
    if not request.user.orkut_profile.check_permission('can_like_comment'):
        messages.error(request, "Your account does not have permission to comment.")
        return redirect('home')

    post = get_object_or_404(StatusPost, id=post_id)
    content = request.POST.get('content', '').strip()
    if content:
        PostComment.objects.create(post=post, author=request.user, content=content)
        if post.author != request.user:
            Notification.objects.create(
                recipient=post.author,
                sender=request.user,
                notification_type='COMMENT',
                title="New Comment",
                message=f"{request.user.orkut_profile.full_name or request.user.username} commented on your status.",
                link='/#post-' + str(post.id)
            )
        messages.success(request, "Comment added.")
    return redirect('home')


@login_required
def delete_comment_view(request, comment_id):
    comment = get_object_or_404(PostComment, id=comment_id)
    if comment.author == request.user or comment.post.author == request.user or request.user.orkut_profile.is_moderator_or_higher():
        comment.delete()
        messages.success(request, "Comment deleted.")
    return redirect('home')


# ----------------- USER PROFILE -----------------

@login_required
def profile_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    profile, _ = UserProfile.objects.get_or_create(
        user=target_user,
        defaults={'full_name': target_user.get_full_name() or target_user.username}
    )

    if not is_self:
        profile.profile_views += 1
        profile.save()
        ProfileVisitor.objects.update_or_create(
            user=target_user,
            visitor=request.user,
            defaults={'visited_at': timezone.now()}
        )

    is_friend = False
    has_sent_request = False
    has_received_request = False
    if not is_self:
        is_friend = Friendship.objects.filter(
            Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
        ).exists()
        if not is_friend:
            has_sent_request = FriendRequest.objects.filter(from_user=request.user, to_user=target_user, status='PENDING').exists()
            has_received_request = FriendRequest.objects.filter(from_user=target_user, to_user=request.user, status='PENDING').exists()

    is_fan = Fan.objects.filter(user=target_user, fan=request.user).exists()
    total_fans = Fan.objects.filter(user=target_user).count()

    friendships = Friendship.objects.filter(Q(user1=target_user) | Q(user2=target_user)).select_related('user1', 'user2', 'user1__orkut_profile', 'user2__orkut_profile')
    friends = [f.user2 if f.user1 == target_user else f.user1 for f in friendships]

    mutual_friends = []
    if not is_self:
        my_friendships = Friendship.objects.filter(Q(user1=request.user) | Q(user2=request.user))
        my_friend_ids = {f.user2_id if f.user1_id == request.user.id else f.user1_id for f in my_friendships}
        mutual_friends = [fr for fr in friends if fr.id in my_friend_ids]

    scraps = ScrapbookMessage.objects.filter(recipient=target_user).select_related('sender', 'sender__orkut_profile')[:8]
    scrap_form = ScrapForm()
    albums = PhotoAlbum.objects.filter(user=target_user).prefetch_related('photos')[:4]
    memberships = CommunityMember.objects.filter(user=target_user, status='ACTIVE').select_related('community')[:8]
    communities = [m.community for m in memberships]

    return render(request, 'profile/profile.html', {
        'target_user': target_user,
        'profile': profile,
        'is_self': is_self,
        'is_friend': is_friend,
        'has_sent_request': has_sent_request,
        'has_received_request': has_received_request,
        'is_fan': is_fan,
        'total_fans': total_fans,
        'friends': friends[:9],
        'total_friends': len(friends),
        'mutual_friends': mutual_friends,
        'scraps': scraps,
        'scrap_form': scrap_form,
        'albums': albums,
        'communities': communities,
    })


@login_required
def edit_profile_view(request):
    if not request.user.orkut_profile.check_permission('can_edit_profile'):
        messages.error(request, "Your account does not have permission to edit profiles.")
        return redirect('home')

    profile, _ = UserProfile.objects.get_or_create(user=request.user)
    if request.method == 'POST':
        form = UserProfileForm(request.POST, request.FILES, instance=profile)
        if form.is_valid():
            form.save()
            messages.success(request, "Your Orkut profile has been updated!")
            return redirect('profile', username=request.user.username)
    else:
        form = UserProfileForm(instance=profile)
    return render(request, 'profile/edit_profile.html', {'form': form})


@login_required
def rate_user_view(request, username):
    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot rate your own profile!")
        return redirect('profile', username=username)
    
    profile = target_user.orkut_profile
    if request.method == 'POST':
        trusty = int(request.POST.get('trusty', profile.trusty_rating))
        cool = int(request.POST.get('cool', profile.cool_rating))
        sexy = int(request.POST.get('sexy', profile.sexy_rating))

        profile.trusty_rating = min(3, max(1, trusty))
        profile.cool_rating = min(3, max(1, cool))
        profile.sexy_rating = min(3, max(1, sexy))
        profile.save()

        messages.success(request, f"You rated {profile.full_name or target_user.username}'s Trusty, Cool, and Sexy meter!")
    return redirect('profile', username=username)


@login_required
def settings_view(request):
    profile = request.user.orkut_profile
    deletion_request = AccountDeletionRequest.objects.filter(user=request.user).first()
    deletion_form = AccountDeletionRequestForm()

    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'fortune':
            profile.fortune = request.POST.get('fortune', profile.fortune)
            profile.save()
            messages.success(request, "Daily fortune status updated!")
        elif action == 'privacy':
            profile.scrapbook_privacy = request.POST.get('scrapbook_privacy', profile.scrapbook_privacy)
            profile.save()
            messages.success(request, "Privacy settings saved!")
        elif action == 'deactivate':
            profile.account_status = 'INACTIVE'
            profile.is_suspended = True
            profile.save()
            logout(request)
            messages.info(request, "Your account has been deactivated. You can log back in anytime to reactivate it.")
            return redirect('login')

    return render(request, 'profile/settings.html', {
        'profile': profile,
        'deletion_request': deletion_request,
        'deletion_form': deletion_form,
    })


# ----------------- TASK 1: USER ACCOUNT DELETION REQUEST -----------------

@login_required
def request_account_deletion_view(request):
    existing_request = AccountDeletionRequest.objects.filter(user=request.user, status__in=['PENDING', 'APPROVED']).first()
    if existing_request:
        messages.warning(request, f"You already have an active deletion request with status '{existing_request.get_status_display()}'.")
        return redirect('settings')

    if request.method == 'POST':
        form = AccountDeletionRequestForm(request.POST)
        if form.is_valid():
            del_req = form.save(commit=False)
            del_req.user = request.user
            del_req.status = 'PENDING'
            del_req.save()

            profile = request.user.orkut_profile
            profile.account_status = 'PENDING_DELETION'
            profile.save()

            # Notify user
            Notification.objects.create(
                recipient=request.user,
                notification_type='DELETION',
                title="Account Deletion Request Submitted",
                message="Your account deletion request has been submitted to the Admin team. Status: Pending.",
                link='/settings/'
            )

            # Audit log
            log_admin_action(request, "DELETION_REQUEST_SUBMITTED", affected_user=request.user, new_val=del_req.reason)

            messages.success(request, "Your account deletion request has been successfully submitted. The administration team will review it.")
            return redirect('settings')
    return redirect('settings')


@login_required
def cancel_account_deletion_view(request, request_id):
    del_req = get_object_or_404(AccountDeletionRequest, id=request_id, user=request.user)
    if del_req.status in ('COMPLETED', 'CANCELLED'):
        messages.error(request, "This deletion request cannot be cancelled.")
        return redirect('settings')

    del_req.status = 'CANCELLED'
    del_req.save()

    profile = request.user.orkut_profile
    profile.account_status = 'ACTIVE'
    profile.save()

    Notification.objects.create(
        recipient=request.user,
        notification_type='DELETION',
        title="Deletion Request Cancelled",
        message="You have successfully cancelled your account deletion request. Your account remains Active.",
        link='/settings/'
    )

    log_admin_action(request, "DELETION_REQUEST_CANCELLED_BY_USER", affected_user=request.user)
    messages.success(request, "Your account deletion request has been cancelled. Your account remains active!")
    return redirect('settings')


# ----------------- SCRAPBOOK -----------------

@login_required
def scrapbook_view(request, username=None):
    if not request.user.orkut_profile.check_permission('can_use_scrapbook'):
        messages.error(request, "Your account does not have permission to use the scrapbook.")
        return redirect('home')

    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    profile = target_user.orkut_profile

    can_scrap = True
    if not is_self:
        if profile.scrapbook_privacy == 'ONLY_ME':
            can_scrap = False
        elif profile.scrapbook_privacy == 'FRIENDS':
            is_friend = Friendship.objects.filter(
                Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
            ).exists()
            if not is_friend:
                can_scrap = False

    if request.method == 'POST':
        if not can_scrap:
            messages.error(request, "This user has restricted scrapbook messages.")
            return redirect('scrapbook', username=target_user.username)
        form = ScrapForm(request.POST)
        if form.is_valid():
            scrap = form.save(commit=False)
            scrap.sender = request.user
            scrap.recipient = target_user
            scrap.save()
            if target_user != request.user:
                Notification.objects.create(
                    recipient=target_user,
                    sender=request.user,
                    notification_type='SCRAP',
                    title="New Scrap Message",
                    message=f"{request.user.orkut_profile.full_name or request.user.username} left a scrap on your scrapbook!",
                    link=f"/scrapbook/{target_user.username}/"
                )
            messages.success(request, "Scrap message posted!")
            return redirect('scrapbook', username=target_user.username)
    else:
        form = ScrapForm()

    scraps = ScrapbookMessage.objects.filter(recipient=target_user).select_related('sender', 'sender__orkut_profile')

    return render(request, 'scrapbook/scrapbook.html', {
        'target_user': target_user,
        'profile': profile,
        'is_self': is_self,
        'scraps': scraps,
        'form': form,
        'can_scrap': can_scrap,
    })


@login_required
def delete_scrap_view(request, scrap_id):
    scrap = get_object_or_404(ScrapbookMessage, id=scrap_id)
    recipient_username = scrap.recipient.username
    if scrap.recipient == request.user or scrap.sender == request.user or request.user.orkut_profile.is_moderator_or_higher():
        scrap.delete()
        messages.success(request, "Scrap removed.")
    return redirect('scrapbook', username=recipient_username)


# ----------------- FRIENDS MANAGEMENT -----------------

@login_required
def friends_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    friendships = Friendship.objects.filter(Q(user1=target_user) | Q(user2=target_user)).select_related('user1', 'user2', 'user1__orkut_profile', 'user2__orkut_profile')
    friends = [f.user2 if f.user1 == target_user else f.user1 for f in friendships]

    q = request.GET.get('q', '').strip()
    if q:
        friends = [f for f in friends if q.lower() in f.username.lower() or (f.orkut_profile.full_name and q.lower() in f.orkut_profile.full_name.lower())]

    return render(request, 'friends/friends_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'friends': friends,
        'total_friends': len(friends),
        'search_query': q,
    })


@login_required
def friend_requests_view(request):
    pending_requests = FriendRequest.objects.filter(to_user=request.user, status='PENDING').select_related('from_user', 'from_user__orkut_profile')
    sent_requests = FriendRequest.objects.filter(from_user=request.user, status='PENDING').select_related('to_user', 'to_user__orkut_profile')
    return render(request, 'friends/requests.html', {
        'pending_requests': pending_requests,
        'sent_requests': sent_requests,
    })


@login_required
def send_friend_request_view(request, username):
    if not request.user.orkut_profile.check_permission('can_send_friend_requests'):
        messages.error(request, "Your account does not have permission to send friend requests.")
        return redirect('profile', username=username)

    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot send a friend request to yourself.")
        return redirect('profile', username=username)

    already_friends = Friendship.objects.filter(
        Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
    ).exists()
    if already_friends:
        messages.info(request, "You are already friends.")
        return redirect('profile', username=username)

    req, created = FriendRequest.objects.get_or_create(from_user=request.user, to_user=target_user)
    if created or req.status != 'PENDING':
        req.status = 'PENDING'
        req.save()
        Notification.objects.create(
            recipient=target_user,
            sender=request.user,
            notification_type='FRIEND_REQ',
            title="New Friend Request",
            message=f"{request.user.orkut_profile.full_name or request.user.username} sent you a friend request!",
            link='/friends/requests/'
        )
        messages.success(request, f"Friend request sent to {target_user.orkut_profile.full_name or target_user.username}!")
    else:
        messages.info(request, "Friend request is already pending.")
    return redirect('profile', username=username)


@login_required
def accept_friend_request_view(request, request_id):
    freq = get_object_or_404(FriendRequest, id=request_id, to_user=request.user, status='PENDING')
    freq.status = 'ACCEPTED'
    freq.save()

    Friendship.objects.get_or_create(user1=freq.from_user, user2=freq.to_user)
    
    Notification.objects.create(
        recipient=freq.from_user,
        sender=request.user,
        notification_type='FRIEND_ACC',
        title="Friend Request Accepted",
        message=f"{request.user.orkut_profile.full_name or request.user.username} accepted your friend request!",
        link=f"/profile/{request.user.username}/"
    )
    messages.success(request, f"You and {freq.from_user.username} are now friends on Orkut!")
    return redirect('friend_requests')


@login_required
def reject_friend_request_view(request, request_id):
    freq = get_object_or_404(FriendRequest, id=request_id, to_user=request.user)
    freq.status = 'REJECTED'
    freq.save()
    messages.info(request, "Friend request rejected.")
    return redirect('friend_requests')


@login_required
def remove_friend_view(request, username):
    target_user = get_object_or_404(User, username=username)
    Friendship.objects.filter(
        Q(user1=request.user, user2=target_user) | Q(user1=target_user, user2=request.user)
    ).delete()
    FriendRequest.objects.filter(
        Q(from_user=request.user, to_user=target_user) | Q(from_user=target_user, to_user=request.user)
    ).delete()
    messages.info(request, f"Removed {target_user.username} from your friends list.")
    return redirect('friends_list')


# ----------------- COMMUNITIES -----------------

@login_required
def communities_list_view(request):
    communities = Community.objects.annotate(member_count=Count('members')).order_by('-created_at')
    category = request.GET.get('category')
    q = request.GET.get('q')
    if category:
        communities = communities.filter(category__icontains=category)
    if q:
        communities = communities.filter(Q(name__icontains=q) | Q(description__icontains=q))

    my_memberships = CommunityMember.objects.filter(user=request.user, status='ACTIVE').values_list('community_id', flat=True)

    return render(request, 'communities/communities_list.html', {
        'communities': communities,
        'my_community_ids': set(my_memberships),
        'search_query': q,
        'category_filter': category,
    })


@login_required
def create_community_view(request):
    if not request.user.orkut_profile.check_permission('can_create_communities'):
        messages.error(request, "Your account does not have permission to create communities.")
        return redirect('communities_list')

    if request.method == 'POST':
        form = CommunityForm(request.POST, request.FILES)
        if form.is_valid():
            comm = form.save(commit=False)
            comm.creator = request.user
            comm.save()
            CommunityMember.objects.create(community=comm, user=request.user, role='OWNER', status='ACTIVE')
            messages.success(request, f"Community '{comm.name}' created successfully!")
            return redirect('community_detail', community_id=comm.id)
    else:
        form = CommunityForm()
    return render(request, 'communities/create_community.html', {'form': form})


@login_required
def community_detail_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    membership = CommunityMember.objects.filter(community=comm, user=request.user).first()
    is_member = membership and membership.status == 'ACTIVE'
    is_pending = membership and membership.status == 'PENDING'
    is_admin = membership and membership.role in ('OWNER', 'MODERATOR')

    members = CommunityMember.objects.filter(community=comm, status='ACTIVE').select_related('user', 'user__orkut_profile')[:12]
    topics = CommunityPost.objects.filter(community=comm).select_related('author', 'author__orkut_profile').annotate(reply_count=Count('replies'))
    topic_form = CommunityPostForm()

    return render(request, 'communities/community_detail.html', {
        'community': comm,
        'membership': membership,
        'is_member': is_member,
        'is_pending': is_pending,
        'is_admin': is_admin,
        'members': members,
        'total_members': comm.members.filter(status='ACTIVE').count(),
        'topics': topics,
        'topic_form': topic_form,
    })


@login_required
def join_community_view(request, community_id):
    if not request.user.orkut_profile.check_permission('can_join_communities'):
        messages.error(request, "Your account does not have permission to join communities.")
        return redirect('communities_list')

    comm = get_object_or_404(Community, id=community_id)
    membership, created = CommunityMember.objects.get_or_create(community=comm, user=request.user)
    if comm.is_private:
        membership.status = 'PENDING'
        membership.save()
        messages.info(request, "Join request submitted. Awaiting community owner approval.")
    else:
        membership.status = 'ACTIVE'
        membership.save()
        messages.success(request, f"You are now a member of '{comm.name}'!")
    return redirect('community_detail', community_id=comm.id)


@login_required
def leave_community_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    if comm.creator == request.user:
        messages.error(request, "The community owner cannot leave the community. You can delete it instead.")
        return redirect('community_detail', community_id=comm.id)
    CommunityMember.objects.filter(community=comm, user=request.user).delete()
    messages.info(request, f"You left '{comm.name}'.")
    return redirect('communities_list')


@login_required
def create_community_topic_view(request, community_id):
    comm = get_object_or_404(Community, id=community_id)
    membership = CommunityMember.objects.filter(community=comm, user=request.user, status='ACTIVE').first()
    if not membership:
        messages.error(request, "You must be a member to start a discussion in this community.")
        return redirect('community_detail', community_id=comm.id)

    if request.method == 'POST':
        form = CommunityPostForm(request.POST)
        if form.is_valid():
            post = form.save(commit=False)
            post.community = comm
            post.author = request.user
            post.save()
            messages.success(request, "Discussion topic created!")
    return redirect('community_detail', community_id=comm.id)


@login_required
def community_topic_detail_view(request, topic_id):
    topic = get_object_or_404(CommunityPost, id=topic_id)
    replies = CommunityComment.objects.filter(topic=topic).select_related('author', 'author__orkut_profile')

    if request.method == 'POST':
        content = request.POST.get('content', '').strip()
        if content:
            CommunityComment.objects.create(topic=topic, author=request.user, content=content)
            messages.success(request, "Reply posted!")
            return redirect('community_topic_detail', topic_id=topic.id)

    return render(request, 'communities/topic_detail.html', {
        'topic': topic,
        'community': topic.community,
        'replies': replies,
    })


# ----------------- PHOTOS & ALBUMS -----------------

@login_required
def photos_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    albums = PhotoAlbum.objects.filter(user=target_user).prefetch_related('photos')
    recent_photos = Photo.objects.filter(user=target_user).select_related('album')[:12]

    album_form = PhotoAlbumForm()
    upload_form = PhotoUploadForm()
    upload_form.fields['album'].queryset = PhotoAlbum.objects.filter(user=request.user)

    return render(request, 'photos/photos_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'albums': albums,
        'recent_photos': recent_photos,
        'album_form': album_form,
        'upload_form': upload_form,
    })


@login_required
def create_album_view(request):
    if not request.user.orkut_profile.check_permission('can_create_albums'):
        messages.error(request, "Your account does not have permission to create photo albums.")
        return redirect('my_photos')

    if request.method == 'POST':
        form = PhotoAlbumForm(request.POST)
        if form.is_valid():
            album = form.save(commit=False)
            album.user = request.user
            album.save()
            messages.success(request, f"Album '{album.title}' created!")
    return redirect('photos_list', username=request.user.username)


@login_required
def upload_photo_view(request):
    if not request.user.orkut_profile.check_permission('can_upload_photos'):
        messages.error(request, "Your account does not have permission to upload photos.")
        return redirect('my_photos')

    if request.method == 'POST':
        form = PhotoUploadForm(request.POST, request.FILES)
        form.fields['album'].queryset = PhotoAlbum.objects.filter(user=request.user)
        if form.is_valid():
            photo = form.save(commit=False)
            photo.user = request.user
            photo.save()
            messages.success(request, "Photo uploaded successfully!")
            return redirect('album_detail', album_id=photo.album.id)
    return redirect('photos_list', username=request.user.username)


@login_required
def album_detail_view(request, album_id):
    album = get_object_or_404(PhotoAlbum, id=album_id)
    photos = Photo.objects.filter(album=album).order_by('-created_at')
    is_owner = album.user == request.user

    upload_form = PhotoUploadForm(initial={'album': album})
    upload_form.fields['album'].queryset = PhotoAlbum.objects.filter(id=album.id)

    return render(request, 'photos/album_detail.html', {
        'album': album,
        'photos': photos,
        'is_owner': is_owner,
        'upload_form': upload_form,
    })


@login_required
def photo_detail_view(request, photo_id):
    photo = get_object_or_404(Photo, id=photo_id)
    comments = PhotoComment.objects.filter(photo=photo).select_related('author', 'author__orkut_profile')

    if request.method == 'POST':
        content = request.POST.get('content', '').strip()
        if content:
            PhotoComment.objects.create(photo=photo, author=request.user, content=content)
            messages.success(request, "Comment added.")
            return redirect('photo_detail', photo_id=photo.id)

    return render(request, 'photos/photo_detail.html', {
        'photo': photo,
        'album': photo.album,
        'comments': comments,
        'is_owner': photo.user == request.user,
    })


@login_required
def delete_photo_view(request, photo_id):
    photo = get_object_or_404(Photo, id=photo_id)
    album_id = photo.album.id
    if photo.user == request.user or request.user.orkut_profile.is_moderator_or_higher():
        photo.delete()
        messages.success(request, "Photo deleted.")
    return redirect('album_detail', album_id=album_id)


# ----------------- PRIVATE MESSAGES / CHAT -----------------

@login_required
def messages_view(request, username=None):
    if not request.user.orkut_profile.check_permission('can_send_messages'):
        messages.error(request, "Your account does not have permission to send private messages.")
        return redirect('home')

    sent_to = PrivateMessage.objects.filter(sender=request.user).values_list('recipient_id', flat=True)
    received_from = PrivateMessage.objects.filter(recipient=request.user).values_list('sender_id', flat=True)
    partner_ids = set(sent_to) | set(received_from)

    partners = User.objects.filter(id__in=partner_ids).select_related('orkut_profile')

    active_partner = None
    chat_messages = []
    if username:
        active_partner = get_object_or_404(User, username=username)
        PrivateMessage.objects.filter(sender=active_partner, recipient=request.user, is_read=False).update(is_read=True)
        chat_messages = PrivateMessage.objects.filter(
            (Q(sender=request.user, recipient=active_partner) | Q(sender=active_partner, recipient=request.user))
        ).order_by('created_at')

    return render(request, 'messages/messages.html', {
        'partners': partners,
        'active_partner': active_partner,
        'chat_messages': chat_messages,
    })


@login_required
def send_message_api(request):
    if not request.user.orkut_profile.check_permission('can_send_messages'):
        return JsonResponse({'status': 'error', 'message': 'Permission denied.'}, status=403)

    if request.method == 'POST':
        recipient_username = request.POST.get('recipient')
        msg_text = request.POST.get('message', '').strip()
        recipient = get_object_or_404(User, username=recipient_username)
        if msg_text:
            pm = PrivateMessage.objects.create(sender=request.user, recipient=recipient, message=msg_text)
            Notification.objects.create(
                recipient=recipient,
                sender=request.user,
                notification_type='MESSAGE',
                title="New Message",
                message=f"New message from {request.user.orkut_profile.full_name or request.user.username}",
                link=f"/messages/{request.user.username}/"
            )
            return JsonResponse({
                'status': 'ok',
                'id': pm.id,
                'message': pm.message,
                'sender': pm.sender.username,
                'time': pm.created_at.strftime('%I:%M %p')
            })
    return JsonResponse({'status': 'error'}, status=400)


@login_required
def get_messages_api(request, username):
    partner = get_object_or_404(User, username=username)
    PrivateMessage.objects.filter(sender=partner, recipient=request.user, is_read=False).update(is_read=True)
    msgs = PrivateMessage.objects.filter(
        (Q(sender=request.user, recipient=partner) | Q(sender=partner, recipient=request.user))
    ).order_by('created_at')

    data = [
        {
            'id': m.id,
            'sender': m.sender.username,
            'is_me': m.sender == request.user,
            'message': m.message,
            'time': m.created_at.strftime('%I:%M %p, %b %d')
        }
        for m in msgs
    ]
    return JsonResponse({'messages': data})


@login_required
def delete_conversation_view(request, username):
    partner = get_object_or_404(User, username=username)
    PrivateMessage.objects.filter(
        (Q(sender=request.user, recipient=partner) | Q(sender=partner, recipient=request.user))
    ).delete()
    messages.info(request, f"Conversation with {partner.username} deleted.")
    return redirect('messages')


# ----------------- FANS & PROFILE VISITORS -----------------

@login_required
def fans_list_view(request, username=None):
    if username is None or username == request.user.username:
        target_user = request.user
        is_self = True
    else:
        target_user = get_object_or_404(User, username=username)
        is_self = False

    fans = Fan.objects.filter(user=target_user).select_related('fan', 'fan__orkut_profile')
    idols = Fan.objects.filter(fan=target_user).select_related('user', 'user__orkut_profile')

    return render(request, 'fans/fans_list.html', {
        'target_user': target_user,
        'is_self': is_self,
        'fans': fans,
        'idols': idols,
    })


@login_required
def toggle_fan_view(request, username):
    target_user = get_object_or_404(User, username=username)
    if target_user == request.user:
        messages.warning(request, "You cannot be your own fan!")
        return redirect('profile', username=username)

    fan_obj = Fan.objects.filter(user=target_user, fan=request.user).first()
    if fan_obj:
        fan_obj.delete()
        messages.info(request, f"You are no longer a fan of {target_user.orkut_profile.full_name or target_user.username}.")
    else:
        Fan.objects.create(user=target_user, fan=request.user)
        Notification.objects.create(
            recipient=target_user,
            sender=request.user,
            notification_type='FAN',
            title="New Fan!",
            message=f"{request.user.orkut_profile.full_name or request.user.username} became your fan!",
            link=f"/fans/{target_user.username}/"
        )
        messages.success(request, f"You are now a fan of {target_user.orkut_profile.full_name or target_user.username}! ❤️")
    return redirect('profile', username=username)


@login_required
def profile_visitors_view(request):
    visitors = ProfileVisitor.objects.filter(user=request.user).select_related('visitor', 'visitor__orkut_profile')
    return render(request, 'fans/visitors.html', {
        'visitors': visitors,
        'total_views': request.user.orkut_profile.profile_views,
    })


# ----------------- NOTIFICATIONS & SEARCH -----------------

@login_required
def notifications_view(request):
    notifs = Notification.objects.filter(recipient=request.user).select_related('sender', 'sender__orkut_profile')
    return render(request, 'dashboard/notifications.html', {'notifications': notifs})


@login_required
def mark_notification_read_view(request, notification_id):
    notif = get_object_or_404(Notification, id=notification_id, recipient=request.user)
    notif.is_read = True
    notif.save()
    return redirect(notif.link or 'notifications')


@login_required
def mark_all_notifications_read_view(request):
    Notification.objects.filter(recipient=request.user, is_read=False).update(is_read=True)
    messages.success(request, "All notifications marked as read.")
    return redirect('notifications')


@login_required
def search_view(request):
    q = request.GET.get('q', '').strip()
    category = request.GET.get('cat', 'all')
    users = []
    communities = []
    posts = []

    if q:
        if category in ('all', 'people'):
            users = User.objects.filter(
                Q(username__icontains=q) |
                Q(orkut_profile__full_name__icontains=q) |
                Q(orkut_profile__city__icontains=q) |
                Q(orkut_profile__profession__icontains=q)
            ).select_related('orkut_profile').distinct()[:15]

        if category in ('all', 'communities'):
            communities = Community.objects.filter(
                Q(name__icontains=q) | Q(description__icontains=q)
            ).annotate(member_count=Count('members'))[:15]

        if category in ('all', 'posts'):
            posts = StatusPost.objects.filter(
                Q(content__icontains=q)
            ).select_related('author', 'author__orkut_profile')[:15]

    return render(request, 'dashboard/search_results.html', {
        'query': q,
        'category': category,
        'users': users,
        'communities': communities,
        'posts': posts,
    })


@login_required
def report_content_view(request):
    if request.method == 'POST':
        content_type = request.POST.get('content_type')
        content_id = request.POST.get('content_id')
        reason = request.POST.get('reason', '').strip()
        if content_type and content_id and reason:
            Report.objects.create(
                reported_by=request.user,
                content_type=content_type,
                content_id=content_id,
                reason=reason
            )
            messages.success(request, "Report submitted. Our moderation team will review this shortly.")
    return redirect('home')


# =========================================================================
# ----------------- TASK 2: COMPLETE ADMIN DASHBOARD ----------------------
# =========================================================================

# Helper to verify administrative access
def check_admin_access(user):
    if not user.is_authenticated:
        return False
    profile = getattr(user, 'orkut_profile', None)
    return user.is_superuser or user.is_staff or (profile and profile.is_admin_or_super())


def check_super_admin_access(user):
    if not user.is_authenticated:
        return False
    profile = getattr(user, 'orkut_profile', None)
    return user.is_superuser or (profile and profile.is_super_admin())


@login_required
def admin_dashboard_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied: Administrator privileges required.")
        return redirect('home')

    # A. KPI Metrics
    total_users = User.objects.count()
    active_users = UserProfile.objects.filter(account_status='ACTIVE', is_suspended=False).count()
    inactive_users = UserProfile.objects.filter(account_status='INACTIVE').count()
    blocked_users = UserProfile.objects.filter(Q(account_status='BLOCKED') | Q(is_suspended=True)).count()
    online_users = UserProfile.objects.filter(is_online=True).count()
    pending_deletions = AccountDeletionRequest.objects.filter(status='PENDING').count()
    total_posts = StatusPost.objects.count()
    total_communities = Community.objects.count()
    total_photos = Photo.objects.count()
    total_friend_requests = FriendRequest.objects.count()

    # Recent Data
    recent_users = User.objects.all().select_related('orkut_profile').order_by('-date_joined')[:10]
    recent_deletions = AccountDeletionRequest.objects.filter(status='PENDING').select_related('user', 'user__orkut_profile')[:5]
    recent_reports = Report.objects.filter(status='PENDING').select_related('reported_by')[:5]
    recent_logs = AdminActivityLog.objects.all().select_related('admin_user', 'affected_user')[:8]

    return render(request, 'admin_panel/dashboard.html', {
        'total_users': total_users,
        'active_users': active_users,
        'inactive_users': inactive_users,
        'blocked_users': blocked_users,
        'online_users': online_users,
        'pending_deletions': pending_deletions,
        'total_posts': total_posts,
        'total_communities': total_communities,
        'total_photos': total_photos,
        'total_friend_requests': total_friend_requests,
        'recent_users': recent_users,
        'recent_deletions': recent_deletions,
        'recent_reports': recent_reports,
        'recent_logs': recent_logs,
        'is_super_admin': check_super_admin_access(request.user),
    })


# ----------------- B. USER MANAGEMENT -----------------

@login_required
def admin_users_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied: Administrator privileges required.")
        return redirect('home')

    q = request.GET.get('q', '').strip()
    status_filter = request.GET.get('status', '')
    role_filter = request.GET.get('role', '')

    users_qs = User.objects.all().select_related('orkut_profile', 'orkut_profile__role').order_by('-date_joined')

    if q:
        users_qs = users_qs.filter(
            Q(username__icontains=q) |
            Q(email__icontains=q) |
            Q(orkut_profile__full_name__icontains=q) |
            Q(orkut_profile__mobile_number__icontains=q)
        )

    if status_filter:
        users_qs = users_qs.filter(orkut_profile__account_status=status_filter)

    if role_filter:
        users_qs = users_qs.filter(orkut_profile__role__id=role_filter)

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

    # Prevent non-superadmins from modifying SuperAdmin accounts
    if target_user.is_superuser and not check_super_admin_access(request.user):
        messages.error(request, "Only a Super Administrator can edit a Super Admin account.")
        return redirect('admin_users')

    if request.method == 'POST':
        form = AdminUserEditForm(request.POST, instance=profile)
        if form.is_valid():
            prev_status = profile.account_status
            prev_role = profile.role.name if profile.role else "None"

            # Update email on user model
            target_user.email = form.cleaned_data['email']
            target_user.first_name = form.cleaned_data['full_name']

            # Only super admin can assign super admin role
            assigned_role = form.cleaned_data.get('role')
            if assigned_role and assigned_role.code == 'SUPER_ADMIN' and not check_super_admin_access(request.user):
                messages.error(request, "You do not have permission to grant Super Admin role.")
                return redirect('admin_users')

            target_user.save()
            updated_profile = form.save()

            new_status = updated_profile.account_status
            new_role = updated_profile.role.name if updated_profile.role else "None"

            log_admin_action(
                request,
                "USER_PROFILE_UPDATED",
                affected_user=target_user,
                prev_val=f"Status: {prev_status}, Role: {prev_role}",
                new_val=f"Status: {new_status}, Role: {new_role}"
            )

            messages.success(request, f"User @{target_user.username} updated successfully!")
            return redirect('admin_users')
    else:
        form = AdminUserEditForm(instance=profile, initial={'email': target_user.email})

    return render(request, 'admin_panel/edit_user.html', {
        'form': form,
        'target_user': target_user,
        'profile': profile,
    })


@login_required
def admin_toggle_user_block(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    if target_user.is_superuser:
        messages.error(request, "Cannot block a Super Admin account.")
        return redirect('admin_users')

    profile = target_user.orkut_profile
    prev_status = profile.account_status
    if profile.account_status == 'BLOCKED' or profile.is_suspended:
        profile.account_status = 'ACTIVE'
        profile.is_suspended = False
        action_name = "USER_UNBLOCKED"
        msg = f"User @{target_user.username} has been unblocked and activated."
    else:
        profile.account_status = 'BLOCKED'
        profile.is_suspended = True
        action_name = "USER_BLOCKED"
        msg = f"User @{target_user.username} has been blocked."

    profile.save()
    log_admin_action(request, action_name, affected_user=target_user, prev_val=prev_status, new_val=profile.account_status)
    messages.success(request, msg)
    return redirect('admin_users')


@login_required
def admin_toggle_user_status(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    if target_user.is_superuser:
        messages.error(request, "Cannot deactivate a Super Admin account.")
        return redirect('admin_users')

    profile = target_user.orkut_profile
    prev_status = profile.account_status
    if profile.account_status == 'ACTIVE':
        profile.account_status = 'INACTIVE'
        action_name = "USER_DEACTIVATED"
        msg = f"User @{target_user.username} deactivated."
    else:
        profile.account_status = 'ACTIVE'
        action_name = "USER_ACTIVATED"
        msg = f"User @{target_user.username} activated."

    profile.save()
    log_admin_action(request, action_name, affected_user=target_user, prev_val=prev_status, new_val=profile.account_status)
    messages.info(request, msg)
    return redirect('admin_users')


@login_required
def admin_reset_user_password(request, user_id):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can perform administrative password resets.")
        return redirect('admin_users')

    target_user = get_object_or_404(User, id=user_id)
    if request.method == 'POST':
        new_password = request.POST.get('new_password', '').strip()
        if len(new_password) >= 6:
            target_user.set_password(new_password)
            target_user.save()
            log_admin_action(request, "PASSWORD_RESET_BY_ADMIN", affected_user=target_user, new_val="New password applied")
            messages.success(request, f"Password for @{target_user.username} has been reset.")
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
    log_admin_action(request, "USER_DELETED_PERMANENTLY", affected_user=None, prev_val=f"Deleted user: @{username}")
    target_user.delete()
    messages.success(request, f"Account @{username} and all associated data have been permanently deleted.")
    return redirect('admin_users')


@login_required
def admin_user_activity_view(request, user_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    target_user = get_object_or_404(User, id=user_id)
    posts = StatusPost.objects.filter(author=target_user)
    scraps_sent = ScrapbookMessage.objects.filter(sender=target_user)
    scraps_received = ScrapbookMessage.objects.filter(recipient=target_user)
    photos = Photo.objects.filter(user=target_user)
    communities = CommunityMember.objects.filter(user=target_user).select_related('community')
    audit_logs = AdminActivityLog.objects.filter(affected_user=target_user)

    return render(request, 'admin_panel/user_activity.html', {
        'target_user': target_user,
        'posts': posts,
        'scraps_sent': scraps_sent,
        'scraps_received': scraps_received,
        'photos': photos,
        'communities': communities,
        'audit_logs': audit_logs,
    })


# ----------------- C. USER AUTHORITIES & PERMISSION MANAGEMENT -----------------

@login_required
def admin_manage_authorities_view(request, user_id=None):
    if not check_super_admin_access(request.user):
        messages.error(request, "Only Super Administrators can manage individual user authorities and permissions.")
        return redirect('admin_dashboard')

    if user_id is None:
        return redirect('admin_users')

    target_user = get_object_or_404(User, id=user_id)
    custom_perm, _ = UserPermission.objects.get_or_create(user=target_user)

    if request.method == 'POST':
        form = UserPermissionForm(request.POST, instance=custom_perm)
        if form.is_valid():
            form.save()
            log_admin_action(request, "USER_PERMISSIONS_UPDATED", affected_user=target_user, new_val="Custom 12 authorities updated")
            Notification.objects.create(
                recipient=target_user,
                notification_type='SYSTEM',
                title="Account Permissions Updated",
                message="An administrator has updated your account feature permissions.",
                link='/settings/'
            )
            messages.success(request, f"Authorities and permissions for @{target_user.username} have been updated!")
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
            log_admin_action(request, "ROLE_CREATED", new_val=role.name)
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
            log_admin_action(request, "ROLE_PERMISSIONS_UPDATED", new_val=f"Role: {role.name}")
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
        return redirect('home')

    status_filter = request.GET.get('status', 'ALL')
    reqs = AccountDeletionRequest.objects.all().select_related('user', 'user__orkut_profile', 'reviewed_by').order_by('-created_at')

    if status_filter != 'ALL':
        reqs = reqs.filter(status=status_filter)

    return render(request, 'admin_panel/deletion_requests.html', {
        'deletion_requests': reqs,
        'status_filter': status_filter,
        'is_super_admin': check_super_admin_access(request.user),
    })


@login_required
def admin_handle_deletion_request(request, request_id, action):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    del_req = get_object_or_404(AccountDeletionRequest, id=request_id)
    prev_status = del_req.status
    notes = request.POST.get('admin_notes', '').strip()

    if action == 'approve':
        del_req.status = 'APPROVED'
        del_req.admin_notes = notes
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        # Update profile to pending deletion
        profile = del_req.user.orkut_profile
        profile.account_status = 'PENDING_DELETION'
        profile.save()

        Notification.objects.create(
            recipient=del_req.user,
            notification_type='DELETION',
            title="Account Deletion Request Approved",
            message=f"Your account deletion request has been approved by administrator @{request.user.username}. Account is scheduled for deletion.",
            link='/settings/'
        )
        log_admin_action(request, "DELETION_REQUEST_APPROVED", affected_user=del_req.user, prev_val=prev_status, new_val='APPROVED')
        messages.success(request, f"Deletion request for @{del_req.user.username} approved.")

    elif action == 'reject':
        del_req.status = 'REJECTED'
        del_req.admin_notes = notes
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        profile = del_req.user.orkut_profile
        profile.account_status = 'ACTIVE'
        profile.save()

        Notification.objects.create(
            recipient=del_req.user,
            notification_type='DELETION',
            title="Account Deletion Request Rejected",
            message=f"Your deletion request was reviewed and rejected. Admin Note: {notes or 'No reason specified'}",
            link='/settings/'
        )
        log_admin_action(request, "DELETION_REQUEST_REJECTED", affected_user=del_req.user, prev_val=prev_status, new_val='REJECTED')
        messages.info(request, f"Deletion request for @{del_req.user.username} rejected.")

    elif action == 'complete':
        # Must be super admin to complete full deletion
        if not check_super_admin_access(request.user):
            messages.error(request, "Only Super Administrators can execute the final account completion & deletion.")
            return redirect('admin_deletion_requests')

        del_req.status = 'COMPLETED'
        del_req.admin_notes = notes or "Account deactivated and data anonymized/cleared per policy."
        del_req.reviewed_by = request.user
        del_req.reviewed_at = timezone.now()
        del_req.save()

        target_u = del_req.user
        profile = target_u.orkut_profile
        profile.account_status = 'DELETED'
        profile.is_suspended = True
        profile.full_name = "Deleted Orkut User"
        profile.about_me = "This account was deleted per user request."
        profile.save()

        log_admin_action(request, "DELETION_REQUEST_COMPLETED", affected_user=target_u, prev_val=prev_status, new_val='COMPLETED')
        messages.success(request, f"Account deletion completed for @{target_u.username}.")

    return redirect('admin_deletion_requests')


# ----------------- E. CONTENT MANAGEMENT -----------------

@login_required
def admin_content_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied.")
        return redirect('home')

    tab = request.GET.get('tab', 'posts')
    posts = StatusPost.objects.all().select_related('author', 'author__orkut_profile').order_by('-created_at')[:25]
    scraps = ScrapbookMessage.objects.all().select_related('sender', 'recipient').order_by('-created_at')[:25]
    photos = Photo.objects.all().select_related('user', 'album').order_by('-created_at')[:25]
    communities = Community.objects.all().select_related('creator').order_by('-created_at')[:25]
    reports = Report.objects.filter(status='PENDING').select_related('reported_by').order_by('-created_at')

    return render(request, 'admin_panel/content.html', {
        'tab': tab,
        'posts': posts,
        'scraps': scraps,
        'photos': photos,
        'communities': communities,
        'reports': reports,
    })


@login_required
def admin_delete_content(request, content_type, content_id):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()

    if content_type == 'post':
        p = get_object_or_404(StatusPost, id=content_id)
        author = p.author
        p.delete()
        log_admin_action(request, "ADMIN_DELETED_POST", affected_user=author)
        messages.success(request, "Post deleted by admin.")
    elif content_type == 'scrap':
        s = get_object_or_404(ScrapbookMessage, id=content_id)
        author = s.sender
        s.delete()
        log_admin_action(request, "ADMIN_DELETED_SCRAP", affected_user=author)
        messages.success(request, "Scrap deleted by admin.")
    elif content_type == 'photo':
        ph = get_object_or_404(Photo, id=content_id)
        owner = ph.user
        ph.delete()
        log_admin_action(request, "ADMIN_DELETED_PHOTO", affected_user=owner)
        messages.success(request, "Photo deleted by admin.")
    elif content_type == 'community':
        c = get_object_or_404(Community, id=content_id)
        cname = c.name
        c.delete()
        log_admin_action(request, "ADMIN_DELETED_COMMUNITY", new_val=cname)
        messages.success(request, f"Community '{cname}' removed by admin.")

    return redirect(f"/admin-dashboard/content/?tab={content_type}s")


# ----------------- F. ADMIN ACTIVITY & AUDIT LOGS -----------------

@login_required
def admin_audit_logs_view(request):
    if not check_admin_access(request.user):
        messages.error(request, "Access Denied.")
        return redirect('home')

    logs_qs = AdminActivityLog.objects.all().select_related('admin_user', 'affected_user').order_by('-created_at')
    action_filter = request.GET.get('action')
    if action_filter:
        logs_qs = logs_qs.filter(action__icontains=action_filter)

    paginator = Paginator(logs_qs, 20)
    page_number = request.GET.get('page')
    logs_page = paginator.get_page(page_number)

    return render(request, 'admin_panel/audit_logs.html', {
        'logs_page': logs_page,
        'action_filter': action_filter,
    })


@login_required
def admin_resolve_report(request, report_id, action):
    if not check_admin_access(request.user):
        return HttpResponseForbidden()
    report = get_object_or_404(Report, id=report_id)
    if action == 'resolve':
        report.status = 'RESOLVED'
        if report.content_type == 'post':
            StatusPost.objects.filter(id=report.content_id).delete()
        elif report.content_type == 'scrap':
            ScrapbookMessage.objects.filter(id=report.content_id).delete()
        elif report.content_type == 'photo':
            Photo.objects.filter(id=report.content_id).delete()
        log_admin_action(request, "REPORT_RESOLVED", new_val=f"{report.content_type} #{report.content_id}")
        messages.success(request, "Report resolved and violating content removed.")
    else:
        report.status = 'DISMISSED'
        log_admin_action(request, "REPORT_DISMISSED", new_val=f"{report.content_type} #{report.content_id}")
        messages.info(request, "Report dismissed.")
    report.save()
    return redirect('admin_dashboard')
